// Separate local preview server; no fixture is bundled into app/.
import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { createFixtureApi, photo } from '../tests/ui/fixtures.mjs';
const role = process.argv.find((arg) => arg.startsWith('--role='))?.slice(7) || 'ADMIN';
if (!['USER', 'BROKER', 'MODERATOR', 'ADMIN'].includes(role)) throw new Error('Use --role=USER|BROKER|MODERATOR|ADMIN');
const api = createFixtureApi(role);
const server = createServer(async (request, response) => {
  response.setHeader('X-UI-Preview', 'synthetic-data');
  if (request.url.startsWith('/__preview/image-')) {
    response.writeHead(200, { 'Content-Type': 'image/svg+xml' });
    response.end(photo(Number(request.url.match(/image-(\d)/)?.[1] || 0)));
    return;
  }
  let input = '';
  for await (const chunk of request) input += chunk;
  let body = {};
  try {
    body = JSON.parse(input || '{}');
  } catch {
    /* unsupported form body */
  }
  const result = api(request.url, request.method, body);
  response.writeHead(result.status, {
    'Content-Type': result.contentType || 'application/json',
  });
  response.end(result.status === 204 ? undefined : result.text || JSON.stringify(result.body));
});
server.listen(4174, '127.0.0.1', () => {
  console.log(
    `UI PREVIEW: synthetic data, role ${role}. Login with preview@example.test and any non-empty password. No real payment or eKYC.`,
  );
  const child = spawn(
    process.execPath,
    ['node_modules/vite/bin/vite.js', '--mode', 'ui-preview', '--host', '127.0.0.1', '--port', '4173'],
    {
      stdio: 'inherit',
      env: { ...process.env, API_INTERNAL_URL: 'http://127.0.0.1:4174' },
    },
  );
  const close = () => {
    child.kill();
    server.close();
  };
  process.on('SIGINT', close);
  process.on('SIGTERM', close);
  child.on('exit', () => server.close());
});
