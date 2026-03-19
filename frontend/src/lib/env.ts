import { z } from 'zod';

const envSchema = z.object({
  NEXT_PUBLIC_APP_URL: z.string().url().default('http://localhost:3000'),
  NEXT_PUBLIC_APP_NAME: z.string().default('Azure SkiShop'),
  API_GATEWAY_URL: z.string().url().default('http://localhost:8080'),
  AUTH_SECRET: z.string().min(1).default('dev-secret-change-in-production'),
  AUTH_URL: z.string().url().default('http://localhost:3000'),
  NEXTAUTH_URL: z.string().url().default('http://localhost:3000'),
  SENTRY_DSN: z.string().optional(),
  NEXT_PUBLIC_SENTRY_DSN: z.string().optional(),
});

export type Env = z.infer<typeof envSchema>;

function validateEnv(): Env {
  const parsed = envSchema.safeParse(process.env);
  if (!parsed.success) {
    console.error('❌ Invalid environment variables:', parsed.error.flatten().fieldErrors);
    throw new Error('Invalid environment variables');
  }
  return parsed.data;
}

export const env = validateEnv();
