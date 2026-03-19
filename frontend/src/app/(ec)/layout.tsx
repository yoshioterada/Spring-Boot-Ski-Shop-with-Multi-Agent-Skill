import { ChatWidget } from '@/components/ec/chat-widget';
import { ECFooter } from '@/components/layout/ec-footer';
import { ECHeader } from '@/components/layout/ec-header';

export default function ECLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <ECHeader />
      <main className="flex-1">{children}</main>
      <ECFooter />
      <ChatWidget />
    </>
  );
}
