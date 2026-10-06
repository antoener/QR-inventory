package com.empresa.inventario.Security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private RateLimitFilter filter(int loginMax, long loginWindow, int globalMax, long globalWindow) {
        return new RateLimitFilter(objectMapper, loginMax, loginWindow, globalMax, globalWindow, "");
    }

    private MockHttpServletRequest loginRequest() {
        return new MockHttpServletRequest("POST", "/api/auth/login");
    }

    private void run(RateLimitFilter filter, MockHttpServletRequest request, MockHttpServletResponse response)
            throws Exception {
        filter.doFilter(request, response, new MockFilterChain());
    }

    @Test
    void login_deberiaPermitirDentroDelLimiteYBloquearAlSuperarlo() throws Exception {
        RateLimitFilter filter = filter(2, 60, 100, 60);

        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse second = new MockHttpServletResponse();
        MockHttpServletResponse third = new MockHttpServletResponse();

        run(filter, loginRequest(), first);
        run(filter, loginRequest(), second);
        run(filter, loginRequest(), third);

        assertEquals(HttpServletResponse.SC_OK, first.getStatus());
        assertEquals(HttpServletResponse.SC_OK, second.getStatus());
        assertEquals(429, third.getStatus());
    }

    @Test
    void rutasFueraDeApi_deberianPasarSinRateLimit() throws Exception {
        RateLimitFilter filter = filter(1, 60, 1, 60);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        run(filter, request, response);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    }

    @Test
    void limiteGlobal_deberiaAplicarARutasDeApi() throws Exception {
        RateLimitFilter filter = filter(100, 60, 1, 60);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products/list");
        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse second = new MockHttpServletResponse();

        run(filter, request, first);
        run(filter, request, second);

        assertEquals(HttpServletResponse.SC_OK, first.getStatus());
        assertEquals(429, second.getStatus());
    }
}
