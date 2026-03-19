import { NextRequest, NextResponse } from 'next/server';
import { getToken } from 'next-auth/jwt';

const protectedPaths = ['/cart', '/checkout', '/mypage'];
const adminPaths = ['/admin'];
const authPaths = ['/login', '/register'];

export async function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  const token = await getToken({
    req: request,
    secret: process.env.AUTH_SECRET,
    cookieName: 'azure-skishop.session-token',
  });

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
  matcher: [
    '/cart/:path*',
    '/checkout/:path*',
    '/mypage/:path*',
    '/admin/:path*',
    '/login',
    '/register',
  ],
};
