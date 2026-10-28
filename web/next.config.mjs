// Strona jest statyczna (GitHub Pages). NEXT_BASE_PATH=/ostoja, gdy strona leży pod adresem repozytorium.
const basePath = process.env.NEXT_BASE_PATH || '';

/** @type {import('next').NextConfig} */
export default {
  output: 'export',
  basePath,
  images: { unoptimized: true },
  trailingSlash: true,
};
