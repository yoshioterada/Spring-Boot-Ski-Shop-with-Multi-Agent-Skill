import { NextRequest, NextResponse } from 'next/server';
import { getToken } from 'next-auth/jwt';

const SESSION_COOKIE_NAME = 'azure-skishop.session-token';
const protectedPaths = ['/cart', '/checkout', '/mypage'];
const adminPaths = ['/admin'];
const authPaths = ['/login', '/register'];

export async function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  let token = null;
  try {
    token = await getToken({
      req: request,
      secret: process.env.NEXTAUTH_SECRET ?? process.env.AUTH_SECRET,
      cookieName: SESSION_COOKIE_NAME,
    });
  } catch {
    // JWT decode failed (invalid/expired cookie) - treat as unauthenticated
  }

  // Clear invalid session cookie: exists but cannot be decoded
  const hasCookie = request.cookies.has(SESSION_COOKIE_NAME);
  if (hasCookie && !token) {
    const isProtected =
      protectedPaths.some((p) => pathname.startsWith(p)) ||
      adminPaths.some((p) => pathname.startsWith(p));
    if (isProtected) {
      const loginUrl = new URL('/login', request.url);
      loginUrl.searchParams.set('redirect', pathname);
      const redirect = NextResponse.redirect(loginUrl);
      redirect.cookies.delete(SESSION_COOKIE_NAME);
      return redirect;
    }
    // Non-protected path: clear the invalid cookie and continue
    const response = NextResponse.next();
    response.cookies.delete(SESSION_COOKIE_NAME);
    return response;
  }

  // Redirect authenticated users away from auth pages
  if (authPaths.some((path) => pathname.startsWith(path))) {
    if (token) {
      return NextResponse.redirect(new URL('/', request.url));
    }
    return NextResponse.next();
  }

  // Protected paths require authentication
  if (protectedPaths.some((path) => pathname.startsWith(path))) {
    if (!token) {
      const loginUrl = new URL('/login', request.url);
      loginUrl.searchParams.set('redirect', pathname);
      return NextResponse.redirect(loginUrl);
    }
    return NextResponse.next();
  }

  // Admin paths require ADMIN or MANAGER role
  if (adminPaths.some((path) => pathname.startsWith(path))) {
    if (!token) {
      const loginUrl = new URL('/login', request.url);
      loginUrl.searchParams.set('redirect', pathname);
      return NextResponse.redirect(loginUrl);
    }

    const role = token.role as string;
    if (role !== 'ADMIN' && role !== 'MANAGER') {
      return NextResponse.redirect(new URL('/', request.url));
    }

    return NextResponse.next();
  }

  return NextResponse.next();
}

export const config = {
  // Match all routes except Next.js internals and static assets
  matcher: [
    '/((?!_next/static|_next/image|favicon.ico).*)',
  ],
};
