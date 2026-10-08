import type { ProductResponse } from '@/types';

export type LabelSize = 30 | 50 | 100;

function getQrBaseUrl(): string {
  const base = import.meta.env.VITE_QR_BASE_URL;
  if (!base) {
    throw new Error('VITE_QR_BASE_URL no está configurada');
  }
  return base.replace(/\/$/, '');
}

export function productQrUrl(product: ProductResponse): string {
  return `${getQrBaseUrl()}/p/${encodeURIComponent(product.code)}`;
}

async function generateQrDataUrl(text: string): Promise<string> {
  const { toDataURL } = await import('qrcode');
  return toDataURL(text, { width: 512, margin: 1, errorCorrectionLevel: 'M' });
}

export async function generateIndividualLabelPdf(
  product: ProductResponse,
  size: LabelSize,
): Promise<void> {
  const [{ jsPDF }, qrDataUrl] = await Promise.all([
    import('jspdf'),
    generateQrDataUrl(productQrUrl(product)),
  ]);

  const doc = new jsPDF({ unit: 'mm', format: [size, size] });
  const margin = 3;
  const qrSize = size - 2 * margin;

  doc.addImage(qrDataUrl, 'PNG', margin, margin, qrSize, qrSize);

  const fontSize = Math.max(8, size / 5);
  doc.setFontSize(fontSize);
  const codeWidth = doc.getTextWidth(product.code);
  doc.text(product.code, (size - codeWidth) / 2, size - margin);

  doc.save(`etiqueta-${product.code}.pdf`);
}

export async function generateBatchLabelsPdf(
  products: ProductResponse[],
  size: LabelSize,
): Promise<void> {
  if (products.length === 0) return;

  const [{ jsPDF }, ...qrDataUrls] = await Promise.all([
    import('jspdf'),
    ...products.map((p) => generateQrDataUrl(productQrUrl(p))),
  ]);

  const pageW = 210;
  const pageH = 297;
  const margin = 15;
  const gap = 2;
  const cols = Math.max(1, Math.floor((pageW - 2 * margin + gap) / (size + gap)));
  const rows = Math.max(1, Math.floor((pageH - 2 * margin + gap) / (size + gap)));
  const perPage = cols * rows;

  const doc = new jsPDF({ unit: 'mm', format: 'a4' });

  for (let i = 0; i < products.length; i++) {
    const cellIdx = i % perPage;
    if (cellIdx === 0 && i > 0) {
      doc.addPage();
    }

    const col = cellIdx % cols;
    const row = Math.floor(cellIdx / cols);
    const x = margin + col * (size + gap);
    const y = margin + row * (size + gap);
    const product = products[i];
    const qrDataUrl = qrDataUrls[i];

    const qrSize = size * 0.65;
    const qrX = x + (size - qrSize) / 2;
    const qrY = y + 2;

    doc.addImage(qrDataUrl, 'PNG', qrX, qrY, qrSize, qrSize);

    doc.setFontSize(7);
    const codeWidth = doc.getTextWidth(product.code);
    doc.text(product.code, x + (size - codeWidth) / 2, y + size - 7);

    doc.setFontSize(6);
    const nameText = product.name.length > 25 ? `${product.name.slice(0, 25)}...` : product.name;
    const nameWidth = doc.getTextWidth(nameText);
    doc.text(nameText, x + (size - nameWidth) / 2, y + size - 3);
  }

  doc.save('etiquetas.pdf');
}

export const labelSizeOptions: { value: LabelSize; label: string }[] = [
  { value: 30, label: '3 × 3 cm' },
  { value: 50, label: '5 × 5 cm' },
  { value: 100, label: '10 × 10 cm' },
];
