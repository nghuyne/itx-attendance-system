// Reads credentials emails out of the CI/local MailHog instance so tests can
// complete the admin-creates-user flow (POST /api/admin/users never returns
// the temp password — see UserManagementService.sendCredentialsEmail — it
// only emails it) without a real SMTP provider.

const MAILHOG_BASE_URL = process.env.MAILHOG_BASE_URL || 'http://localhost:8025';

// UserManagementService.TEMP_PASSWORD_CHARS excludes ambiguous chars
// (0/O/1/I/l), length is always 12. Matching on this shape is more robust
// than matching the (localized, possibly quoted-printable-encoded) label
// text around it.
const TEMP_PASSWORD_PATTERN = /\b([A-HJ-NP-Za-km-z2-9]{12})\b/;

function decodeQuotedPrintable(input: string): string {
  const bytes: number[] = [];
  for (let i = 0; i < input.length; i++) {
    const ch = input[i];
    if (ch === '=') {
      if (input[i + 1] === '\r' && input[i + 2] === '\n') {
        i += 2;
        continue;
      }
      if (input[i + 1] === '\n') {
        i += 1;
        continue;
      }
      const hex = input.slice(i + 1, i + 3);
      if (/^[0-9A-Fa-f]{2}$/.test(hex)) {
        bytes.push(parseInt(hex, 16));
        i += 2;
        continue;
      }
    }
    bytes.push(...Buffer.from(ch, 'utf-8'));
  }
  return Buffer.from(bytes).toString('utf-8');
}

async function findLatestEmailBody(recipient: string): Promise<string | null> {
  const res = await fetch(
    `${MAILHOG_BASE_URL}/api/v2/search?kind=to&query=${encodeURIComponent(recipient)}`
  );
  if (!res.ok) return null;
  const { items } = (await res.json()) as { items: Array<{ Content: { Body: string } }> };
  if (!items?.length) return null;
  return decodeQuotedPrintable(items[0].Content.Body);
}

// Polls MailHog until the credentials email for `recipient` shows up, then
// extracts the 12-char temp password from it.
export async function waitForTempPassword(recipient: string, timeoutMs = 15_000): Promise<string> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const body = await findLatestEmailBody(recipient);
    const match = body?.match(TEMP_PASSWORD_PATTERN);
    if (match) return match[1];
    await new Promise(resolve => setTimeout(resolve, 500));
  }
  throw new Error(`No credentials email arrived for ${recipient} within ${timeoutMs}ms`);
}
