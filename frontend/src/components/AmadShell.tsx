"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Activity, BarChart3, Box, ChevronDown, Database, Layers, ListTree } from "lucide-react";
import type { ReactNode } from "react";

const NAV_ITEMS = [
  { name: "Data Sources", href: "/sources", icon: Database },
  { name: "Source Explorer", href: "/sources?explorer=1", icon: ListTree },
  { name: "Storage", href: "http://localhost:9101", icon: Box, external: true },
  { name: "Storage Jobs", href: "/jobs", icon: Activity },
];

export function AmadShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();

  const breadcrumb = () => {
    if (pathname === "/sources/new") {
      return (
        <div className="flex items-center gap-2 text-xs text-neutral-500">
          <Link href="/sources" className="hover:text-neutral-900">Data Sources</Link>
          <span>›</span>
          <span className="font-semibold text-neutral-900">Ajouter une connexion SQL Server</span>
        </div>
      );
    }
    if (pathname === "/sources") {
      return <span className="text-xs font-semibold text-neutral-900">Data Sources</span>;
    }
    if (pathname === "/jobs") {
      return <span className="text-xs font-semibold text-neutral-900">Storage Jobs</span>;
    }
    if (pathname === "/analytics") {
      return <span className="text-xs font-semibold text-neutral-900">Trino Analytics</span>;
    }
    return <span className="text-xs font-semibold text-neutral-900">Data Platform</span>;
  };

  return (
    <div className="min-h-screen bg-white flex font-sans text-neutral-900">
      {/* Wireframe Left Sidebar (#f5f5f5) */}
      <aside className="w-56 bg-[#f5f5f5] border-r border-neutral-200 flex flex-col fixed inset-y-0 left-0 z-30">
        <div className="h-14 flex items-center gap-2.5 px-4">
          <div className="h-7 w-7 bg-[#27272a] rounded-md flex items-center justify-center text-white">
            <Layers className="h-4 w-4" />
          </div>
          <span className="font-bold text-sm tracking-tight text-neutral-900">Data Platform</span>
        </div>

        <nav className="flex-1 py-2 px-3 space-y-1">
          {NAV_ITEMS.map(({ name, href, icon: Icon, external }) => {
            const active =
              !external &&
              ((href === "/sources" && pathname.startsWith("/sources")) || pathname === href);
            if (external) {
              return (
                <a
                  key={name}
                  href={href}
                  target="_blank"
                  rel="noreferrer"
                  className="flex items-center gap-2.5 px-3 py-2 rounded-md text-xs font-medium text-neutral-600 hover:text-neutral-900 hover:bg-neutral-200/60"
                >
                  <Icon className="h-4 w-4 text-neutral-500" />
                  <span>{name}</span>
                </a>
              );
            }
            return (
              <Link
                key={name}
                href={href}
                className={`flex items-center gap-2.5 px-3 py-2 rounded-md text-xs transition ${
                  active
                    ? "bg-white border border-neutral-300 text-neutral-900 font-semibold shadow-2xs"
                    : "text-neutral-600 hover:text-neutral-900 hover:bg-neutral-200/60 font-medium"
                }`}
              >
                <Icon className={`h-4 w-4 ${active ? "text-neutral-900" : "text-neutral-500"}`} />
                <span>{name}</span>
              </Link>
            );
          })}

          <div className="pt-5 pb-1 px-3">
            <div className="text-[10px] font-semibold uppercase tracking-wider text-neutral-400">FinLakeAmad</div>
          </div>
          <Link
            href="/analytics"
            className={`flex items-center gap-2.5 px-3 py-2 rounded-md text-xs ${
              pathname === "/analytics"
                ? "bg-white border border-neutral-300 text-neutral-900 font-semibold"
                : "text-neutral-600 hover:text-neutral-900 hover:bg-neutral-200/60 font-medium"
            }`}
          >
            <BarChart3 className="h-4 w-4 text-neutral-500" />
            <span>Trino Analytics</span>
          </Link>
        </nav>
      </aside>

      {/* Main White Workspace */}
      <div className="flex-1 ml-56 flex flex-col min-w-0 bg-white">
        <header className="h-14 bg-white border-b border-neutral-200 px-8 flex items-center justify-between sticky top-0 z-20">
          {breadcrumb()}
          <div className="flex items-center gap-2 text-xs text-neutral-700">
            <span className="h-7 w-7 rounded-full bg-neutral-200 text-neutral-700 font-semibold flex items-center justify-center text-[11px]">
              AK
            </span>
            <span className="font-medium">Amad Platform</span>
            <ChevronDown className="h-3.5 w-3.5 text-neutral-400" />
          </div>
        </header>
        <main className="flex-1 px-8 py-7 max-w-6xl w-full">{children}</main>
      </div>
    </div>
  );
}
