import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { useForgotPassword } from '@/features/auth/hooks';
import { useAuthRateLimit } from '@/features/auth/hooks/useRateLimit';
import { ApiClientError } from '@/api';

const forgotPasswordSchema = z.object({
  email: z.string().email('Invalid email').min(1, 'Email is required'),
});

type ForgotPasswordFormData = z.infer<typeof forgotPasswordSchema>;

// Supply Chain Network Pattern - subtle animated nodes and routes
const SUPPLY_CHAIN_PATTERN = "data:image/svg+xml,%3Csvg width='60' height='60' viewBox='0 0 60 60' xmlns='http://www.w3.org/2000/svg'%3E%3Cdefs%3E%3Cstyle%3E.flow-line%7Bstroke%3A%231e3a5f%3Bstroke-width%3A1%3Bfill%3Anone%3Bstroke-dasharray%3A8%204%3Banimation%3Aflow%2025s%20linear%20infinite%3B%7D.node%7Bfill%3A%231e3a5f%3Bopacity%3A0.6%3B%7D%40keyframes%20flow%7Bto%7Bstroke-dashoffset%3A-12%3B%7D%7D%3C%2Fstyle%3E%3C%2Fdefs%3E%3Cline%20class%3D%22flow-line%22%20x1%3D%220%22%20y1%3D%2220%22%20x2%3D%2260%22%20y2%3D%2220%22%2F%3E%3Cline%20class%3D%22flow-line%22%20x1%3D%220%22%20y1%3D%2240%22%20x2%3D%2260%22%20y2%3D%2240%22%2F%3E%3Cline%20class%3D%22flow-line%22%20x1%3D%2220%22%20y1%3D%220%22%20x2%3D%2220%22%20y2%3D%2260%22%2F%3E%3Cline%20class%3D%22flow-line%22%20x1%3D%2240%22%20y1%3D%220%22%20x2%3D%2240%22%20y2%3D%2260%22%2F%3E%3Cline%20class%3D%22flow-line%22%20x1%3D%220%22%20y1%3D%220%22%20x2%3D%2220%22%20y2%3D%2220%22%20opacity%3D%220.3%22%2F%3E%3Cline%20class%3D%22flow-line%22%20x1%3D%2240%22%20y1%3D%220%22%20x2%3D%2260%22%20y2%3D%2220%22%20opacity%3D%220.3%22%2F%3E%3Cline%20class%3D%22flow-line%22%20x1%3D%220%22%20y1%3D%2240%22%20x2%3D%2220%22%20y2%3D%2260%22%20opacity%3D%220.3%22%2F%3E%3Cline%20class%3D%22flow-line%22%20x1%3D%2240%22%20y1%3D%2240%22%20x2%3D%2260%22%20y2%3D%2260%22%20opacity%3D%220.3%22%2F%3E%3Ccircle%20class%3D%22node%22%20cx%3D%2220%22%20cy%3D%2220%22%20r%3D%222%22%2F%3E%3Ccircle%20class%3D%22node%22%20cx%3D%2240%22%20cy%3D%2220%22%20r%3D%222%22%2F%3E%3Ccircle%20class%3D%22node%22%20cx%3D%2220%22%20cy%3D%2240%22%20r%3D%222%22%2F%3E%3Ccircle%20class%3D%22node%22%20cx%3D%2240%22%20cy%3D%2240%22%20r%3D%222%22%2F%3E%3Ccircle%20class%3D%22node%22%20cx%3D%2230%22%20cy%3D%2230%22%20r%3D%221.5%22%20opacity%3D%220.4%22%2F%3E%3C%2Fsvg%3E";

const NOISE_PATTERN = "data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noise)'/%3E%3C/svg%3E";

const QRCodeIcon = () => (
  <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={2} className="w-7 h-7 text-white">
    <path strokeLinecap="round" strokeLinejoin="round" d="M9 11l3 3L22 4" />
    <path strokeLinecap="round" strokeLinejoin="round" d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h7" />
  </svg>
);

export function ForgotPasswordPage() {
  const navigate = useNavigate();
  const forgotPassword = useForgotPassword();
  const { isRateLimited, remainingSeconds, progress, startCountdown } = useAuthRateLimit();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitted },
    reset,
  } = useForm<ForgotPasswordFormData>({
    resolver: zodResolver(forgotPasswordSchema),
  });

  const onSubmit = async (data: ForgotPasswordFormData) => {
    try {
      await forgotPassword.mutateAsync(data.email);
      reset();
    } catch (error) {
      if (error instanceof ApiClientError && error.status === 429 && error.data?.retryAfter) {
        startCountdown(error.data.retryAfter, 'forgot-password');
      }
    }
  };

  const showSuccessMessage = isSubmitted && !forgotPassword.isPending && !isRateLimited;

  return (
    <div className="relative min-h-screen flex items-center justify-center px-4 overflow-hidden">
      <div className="absolute inset-0 bg-gradient-to-br from-slate-50 via-blue-50/30 to-indigo-50/20" />
      <div className="absolute inset-0 opacity-15" style={{ backgroundImage: `url("${SUPPLY_CHAIN_PATTERN}")` }} />
      <div className="absolute inset-0 opacity-5" style={{ backgroundImage: `url("${NOISE_PATTERN}")` }} />

      <Card className="relative w-full max-w-sm z-10
        shadow-[0_10px_40px_-10px_rgba(30,58,95,0.15),0_2px_10px_-2px_rgba(30,58,95,0.1)]
        rounded-2xl border border-slate-200/50
        bg-white/90 backdrop-blur-sm
        animate-slide-up-fade">
        <CardHeader className="text-center pb-2">
          <div className="w-12 h-12 mx-auto mb-4 rounded-xl bg-gradient-to-br from-blue-600 to-indigo-600
            flex items-center justify-center shadow-lg shadow-blue-500/25 animate-fade-in animate-delay-100">
            <QRCodeIcon />
          </div>
          <CardTitle className="text-center text-2xl font-semibold text-slate-900 tracking-tight animate-fade-in animate-delay-200">
            Recover password
          </CardTitle>
          <p className="text-center text-sm text-slate-500 mt-1 animate-fade-in animate-delay-300">
            Enter your email and we will send you a link to reset it
          </p>
        </CardHeader>
        <CardContent>
          {isRateLimited && (
            <div className="relative mb-4 animate-fade-in animate-delay-300">
              <div className="h-2 bg-gray-200 rounded overflow-hidden">
                <div
                  className="h-full bg-red-500 transition-all duration-1000"
                  style={{ width: `${progress}%` }}
                />
              </div>
              <span className="absolute right-0 top-full mt-1 text-xs text-red-600">
                Try again in {remainingSeconds}s
              </span>
            </div>
          )}
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 animate-fade-in animate-delay-300">
            <div style={{ animationDelay: '400ms' }} className="animate-fade-in">
              <Input
                label="Email"
                type="email"
                autoComplete="email"
                {...register('email')}
                error={errors.email?.message}
                disabled={isRateLimited}
              />
            </div>
            {showSuccessMessage && (
              <div className="rounded-lg bg-green-50 p-3 text-sm text-green-700 animate-fade-in" style={{ animationDelay: '450ms' }}>
                If the email exists in our system, you will receive a link to reset your password.
              </div>
            )}
            <Button
              type="submit"
              className="w-full h-11 text-base font-medium rounded-xl
                bg-gradient-to-r from-blue-600 to-indigo-600
                hover:from-blue-700 hover:to-indigo-700
                active:from-blue-800 active:to-indigo-800
                shadow-lg shadow-blue-500/30
                transition-all duration-200
                disabled:opacity-50 disabled:cursor-not-allowed disabled:shadow-none"
              disabled={forgotPassword.isPending || isRateLimited}
            >
              {forgotPassword.isPending ? 'Sending...' : 'Send link'}
            </Button>
          </form>
          <div className="mt-4 text-center animate-fade-in" style={{ animationDelay: '500ms' }}>
            <p className="text-sm text-gray-600">
              Remembered your password?{' '}
              <button
                type="button"
                onClick={() => navigate('/login')}
                className="text-primary-600 hover:underline transition-colors font-medium"
              >
                Sign in
              </button>
            </p>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}