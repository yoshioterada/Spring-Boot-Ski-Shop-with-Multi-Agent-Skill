import dynamic from 'next/dynamic';

const AdminSidebar = dynamic(
  () => import('@/components/layout/admin-sidebar').then((m) => ({ default: m.AdminSidebar })),
  {
    loading: () => <div className="bg-muted w-64 animate-pulse" />,
  },
);

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-screen">
      <AdminSidebar />
      <main className="flex-1 overflow-auto">
        <div className="p-6">{children}</div>
      </main>
    </div>
  );
}
