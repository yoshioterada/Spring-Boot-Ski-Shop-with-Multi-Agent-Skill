import { FileQuestion } from 'lucide-react';
import Link from 'next/link';

export default function NotFound() {
  return (
    <div className="flex min-h-[50vh] flex-col items-center justify-center gap-4 text-center">
      <FileQuestion className="text-muted-foreground h-16 w-16" />
      <h1 className="text-2xl font-bold">ページが見つかりません</h1>
      <p className="text-muted-foreground max-w-md">
        お探しのページは存在しないか、移動した可能性があります。
      </p>
      <Link
        href="/"
        className="bg-primary text-primary-foreground hover:bg-primary/80 inline-flex h-8 items-center justify-center rounded-lg px-2.5 text-sm font-medium transition-all"
      >
        ホームに戻る
      </Link>
    </div>
  );
}
