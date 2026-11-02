import { useState } from 'react';

const amounts = [5, 10, 25] as const;

function donateTemplate() {
  return import.meta.env.VITE_PAYPAL_DONATE_URL?.trim() ?? '';
}

function paypalHref(amount: number): string | null {
  const template = donateTemplate();
  if (!template) {
    return null;
  }
  let url: URL;
  try {
    url = new URL(template.replaceAll('{amount}', String(amount)));
  } catch {
    return null;
  }
  const host = url.hostname.toLowerCase();
  const paypalHost =
    host === 'paypal.com' ||
    host === 'www.paypal.com' ||
    host === 'paypal.me' ||
    host === 'www.paypal.me';
  if (url.protocol !== 'https:' || !paypalHost) {
    return null;
  }
  return url.toString();
}

export function DonatePanel() {
  const [open, setOpen] = useState(false);
  const [amount, setAmount] = useState<(typeof amounts)[number]>(10);
  const template = donateTemplate();
  const chooseAmount = template === '' || template.includes('{amount}');
  const href = paypalHref(amount);

  if (!open) {
    return (
      <button type="button" className="donate-launcher" onClick={() => setOpen(true)}>
        <span aria-hidden="true">♥</span> Donate
      </button>
    );
  }

  return (
    <aside className="donate-panel" aria-label="Donate">
      <header className="donate-header">
        <h3>Support American Trends</h3>
        <button type="button" className="chat-close" onClick={() => setOpen(false)} aria-label="Close donate">
          ×
        </button>
      </header>
      <p className="donate-copy">
        This site is independent. A donation helps cover hosting and the data behind the charts.
      </p>
      {chooseAmount && (
        <div className="donate-amounts" role="group" aria-label="Donation amount in US dollars">
          {amounts.map((value) => (
            <button
              key={value}
              type="button"
              className={value === amount ? 'active' : undefined}
              aria-pressed={value === amount}
              onClick={() => setAmount(value)}
            >
              ${value}
            </button>
          ))}
        </div>
      )}
      {href ? (
        <a className="donate-paypal" href={href} target="_blank" rel="noopener noreferrer">
          Continue with PayPal
        </a>
      ) : (
        <button type="button" className="donate-paypal" disabled>
          Continue with PayPal
        </button>
      )}
      <p className="donate-note">
        {href
          ? 'PayPal handles the payment. This site never sees your card.'
          : template
            ? 'That PayPal link isn’t usable. It needs to be an https://www.paypal.com or https://www.paypal.me address.'
            : 'Your PayPal donate link isn’t connected yet.'}
      </p>
    </aside>
  );
}
