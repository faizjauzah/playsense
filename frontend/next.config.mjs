const isDev = process.env.NODE_ENV === "development";
const backend = process.env.BACKEND_ORIGIN ?? "http://localhost:8080";

/** Static export: the pages ship inside the Spring Boot jar. The rewrite exists only so `next dev` talks to the API. */
export default {
  output: "export",
  trailingSlash: true,
  images: { unoptimized: true },
  ...(isDev
    ? {
        async rewrites() {
          return [{ source: "/api/:path*", destination: `${backend}/api/:path*` }];
        },
      }
    : {}),
};
