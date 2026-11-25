import type { Metadata } from "next";
import type { ReactNode } from "react";
import { AmadShell } from "@/components/AmadShell";
import "./globals.css";

export const metadata: Metadata = {
  title: "FinLakeAmad Console",
  description: "Local enterprise financial transaction data lake (PaySim)",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>
        <AmadShell>{children}</AmadShell>
      </body>
    </html>
  );
}
