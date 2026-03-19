import { defineConfig } from 'orval';

export default defineConfig({
  skishop: {
    input: {
      target: './openapi/api-gateway.yaml',
    },
    output: {
      mode: 'tags-split',
      target: './src/bff/generated',
      schemas: './src/types/generated',
      client: 'react-query',
      httpClient: 'fetch',
      override: {
        mutator: {
          path: './src/lib/api-client.ts',
          name: 'customFetch',
        },
        query: {
          useQuery: true,
          useMutation: true,
        },
      },
    },
  },
});
