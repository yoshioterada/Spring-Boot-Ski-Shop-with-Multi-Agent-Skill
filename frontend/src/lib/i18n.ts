import jaTranslations from '@/locales/ja.json';

function getNestedValue(obj: Record<string, unknown>, path: string): string {
  const keys = path.split('.');
  let current: unknown = obj;

  for (const key of keys) {
    if (current === null || current === undefined || typeof current !== 'object') {
      return path;
    }
    current = (current as Record<string, unknown>)[key];
  }

  return typeof current === 'string' ? current : path;
}

export function t(key: string, params?: Record<string, string | number>): string {
  let value = getNestedValue(jaTranslations as Record<string, unknown>, key);

  if (params) {
    Object.entries(params).forEach(([paramKey, paramValue]) => {
      value = value.replace(`{${paramKey}}`, String(paramValue));
    });
  }

  return value;
}

export type TranslationKey = string;
