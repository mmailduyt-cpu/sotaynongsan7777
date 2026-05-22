# DThanh Farm Application Guidelines

This document contains persistent project rules, architectural layouts, and core logic parameters for AI Coding Assistants. Please preserve these attributes when iterating on this codebase.

## 1. Core Visual Layout & Identity
- **Name**: "DThanh Farm" (Subtitle: "Sổ tay nông sản" in Vietnamese).
- **Style Concept**: High-contrast modern organic theme. Warm green accents (`#214D3A`, `#1B5E20`) and alert orange highlights (`#E65100`) to differentiate quality types.
- **Architecture Flow**: Single-page container using structural desktop dual columns:
  - **Left column (lg:col-span-5)**: Input category panel of agricultural product entries.
  - **Right column (lg:col-span-7)**: Multi-tab layout hosting historical record sheets (Tab: "Sổ tay Lịch sử") and analytics metrics (Tab: "Tổng kết Vụ mùa").

## 2. Core Operational & Business Logic
- **Product Categorization**: Every harvests item is divided solely into two quality sections:
  1. **Hàng ngon** (Premium Standard, Good condition crops).
  2. **Hàng dạt** (Secondary standard, Low-grade condition crops).
- **Price-Collision Logic**: 
  - When saving or editing a harvest with key coordinates (`Date`, `ProductName`):
    - **Separate Rows**: If the prices of either standard (good standard or bad standard) differ from existing entries on that date, keep them as **distinct lines**. Do NOT average or blend prices.
    - **Combined Summing**: If the incoming entries feature exactly identical prices to an already saved line of the same crop on that date, they must **automatically accumulate** their total weight (good records sum together, bad records sum together).
- **Local Storage Reliability**: App state must persist reliably across sessions via localStorage (`dthanh_farm_records`).

## 3. Technology Stack & Deployment Support
- **Engine**: Single Page Application (SPA) powered by React 18, Vite, and tailwindcss v4.
- **Lunar Calendar**: Vietnamese Lunar Calendar support based on core mathematical transformations inside `src/utils/lunar.ts`.
- **Packaging Compatibility**: 
  - Standard Vite development environment.
  - Production builds output cleanly into `dist/`. No server-side reverse proxying or third-party backend servers are required; it runs fully client-side.
