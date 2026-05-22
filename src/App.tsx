import { useState, useEffect, useRef, ChangeEvent } from "react";
import { motion, AnimatePresence } from "motion/react";
import {
  Calendar,
  Settings,
  Plus,
  Trash2,
  Edit2,
  Check,
  X,
  Download,
  Upload,
  ChevronDown,
  ChevronUp,
  AlertCircle,
  Database,
  Sprout,
  BarChart2,
  History,
  FileSpreadsheet,
  RefreshCw
} from "lucide-react";
import { lunarTextFromDateTime } from "./utils/lunar";

// Structure matches com.example.data.Entities
interface HarvestRecord {
  id: string;
  dateTime: string; // YYYY-MM-DD
  product: string;
  goodQty: number;
  badQty: number;
  goodPrice: number;
  badPrice: number;
}

interface Product {
  name: string;
}

// Format currency to VND format elegantly
function formatVnd(amount: number): string {
  const formatter = new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0
  });
  return formatter.format(amount).replace("₫", "đ").trim();
}

function calcRevenue(record: HarvestRecord): number {
  return record.goodQty * record.goodPrice + record.badQty * record.badPrice;
}

function formatDisplayDate(dateStr: string): string {
  if (!dateStr) return "";
  try {
    const parts = dateStr.split("-");
    if (parts.length === 3) {
      return `${parts[2]}/${parts[1]}/${parts[0]}`;
    }
    return dateStr;
  } catch (e) {
    return dateStr;
  }
}

// Organic theme color accent matching the crop name
function getProductColorAccent(name: string): string {
  let total = 0;
  for (let i = 0; i < name.length; i++) {
    total += name.charCodeAt(i);
  }
  const colors = [
    "#2E7D32", // Forest Emerald
    "#EF6C00", // Pumpkin Hue
    "#D84315", // Rustic Terra Cotta
    "#00695C", // Deep Teal Green
    "#689F38"  // Organic Green Apple
  ];
  return colors[total % colors.length];
}

const logoUrl = new URL("../app/src/main/res/drawable/logo_no_text_1779382711881.png", import.meta.url).href;

export default function App() {
  // Load products list defaulted to: Mướp, Đậu đũa, Bầu, Bí đao
  const [products, setProducts] = useState<Product[]>(() => {
    const cached = localStorage.getItem("dthanh_farm_products");
    if (cached) {
      try {
        return JSON.parse(cached);
      } catch (e) {
        // ignore
      }
    }
    return [
      { name: "Mướp" },
      { name: "Đậu đũa" },
      { name: "Bầu" },
      { name: "Bí đao" }
    ];
  });

  // Load harvest records with default samples that align with default categories
  const [records, setRecords] = useState<HarvestRecord[]>(() => {
    const cached = localStorage.getItem("dthanh_farm_records");
    if (cached) {
      try {
        return JSON.parse(cached);
      } catch (e) {
        // ignore
      }
    }
    const todayStr = new Date().toISOString().substring(0, 10);
    return [
      {
        id: "r1",
        dateTime: todayStr,
        product: "Bầu",
        goodQty: 45,
        goodPrice: 5000,
        badQty: 13,
        badPrice: 2500
      },
      {
        id: "r2",
        dateTime: todayStr,
        product: "Mướp",
        goodQty: 30,
        goodPrice: 6000,
        badQty: 5,
        badPrice: 3000
      }
    ];
  });

  // Save changes locally
  useEffect(() => {
    localStorage.setItem("dthanh_farm_products", JSON.stringify(products));
  }, [products]);

  useEffect(() => {
    localStorage.setItem("dthanh_farm_records", JSON.stringify(records));
  }, [records]);

  // Tab State: "input" | "history" | "summary"
  const [activeTab, setActiveTab] = useState<string>("input");

  // Form states
  const [formDate, setFormDate] = useState<string>(() => new Date().toISOString().substring(0, 10));
  const [formProduct, setFormProduct] = useState<string>("");
  const [formGoodQty, setFormGoodQty] = useState<string>("0");
  const [formGoodPrice, setFormGoodPrice] = useState<string>("0");
  const [formBadQty, setFormBadQty] = useState<string>("0");
  const [formBadPrice, setFormBadPrice] = useState<string>("0");

  // Automatically select the first product when the list changes
  useEffect(() => {
    if ((!formProduct || !products.some(p => p.name === formProduct)) && products.length > 0) {
      setFormProduct(products[0].name);
    }
  }, [products, formProduct]);

  // UI state managers
  const [showProductManager, setShowProductManager] = useState(false);
  const [newProductNameInput, setNewProductNameInput] = useState("");

  // Record Inline Editing states
  const [editingRecordId, setEditingRecordId] = useState<string | null>(null);
  const [editProduct, setEditProduct] = useState("");
  const [editGoodQty, setEditGoodQty] = useState("0");
  const [editGoodPrice, setEditGoodPrice] = useState("0");
  const [editBadQty, setEditBadQty] = useState("0");
  const [editBadPrice, setEditBadPrice] = useState("0");

  // Confirmation modaling overlays
  const [customAlert, setCustomAlert] = useState<{ message: string; isError: boolean } | null>(null);
  const [toast, setToast] = useState<{ message: string } | null>(null);
  const [pendingDeleteRecord, setPendingDeleteRecord] = useState<HarvestRecord | null>(null);
  const [pendingDeleteProduct, setPendingDeleteProduct] = useState<Product | null>(null);
  const [showResetConfirm, setShowResetConfirm] = useState(false);

  // Expanded dates state map (collapsed by default as requested: "tự động thu vào khi cần ấn mới hiện")
  const [collapsedDates, setCollapsedDates] = useState<Record<string, boolean>>({});

  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (toast) {
      const timer = setTimeout(() => {
        setToast(null);
      }, 2500);
      return () => clearTimeout(timer);
    }
  }, [toast]);

  const showNotice = (msg: string, isError: boolean) => {
    if (!isError) {
      setToast({ message: msg });
    } else {
      setCustomAlert({ message: msg, isError: true });
    }
  };

  const handleAddHarvestRecord = () => {
    const goodQ = parseFloat(formGoodQty) || 0;
    const goodP = parseFloat(formGoodPrice) || 0;
    const badQ = parseFloat(formBadQty) || 0;
    const badP = parseFloat(formBadPrice) || 0;

    if (!formDate || !formProduct) {
      showNotice("Lỗi: Vui lòng lựa chọn đầy đủ ngày và loại nông sản gieo trồng.", true);
      return;
    }

    if (goodQ < 0 || goodP < 0 || badQ < 0 || badP < 0) {
      showNotice("Lỗi: Số lượng và đơn giá nông sản không thể nhỏ hơn 0.", true);
      return;
    }

    if (goodQ === 0 && badQ === 0 && goodP === 0 && badP === 0) {
      showNotice("Ghi chép đang rỗng. Vui lòng điền số lượng và đơn giá vụ mùa thu hoạch.", true);
      return;
    }

    if (goodQ === 0 && badQ === 0) {
      showNotice("Bạn đã thiết lập đơn giá nhưng chưa nhập số lượng thu hoạch.", true);
      return;
    }

    if (goodQ > 0 && goodP === 0) {
      showNotice("Loại hàng ngon đang có số lượng sản lượng nhưng chưa được điền đơn giá.", true);
      return;
    }

    if (badQ > 0 && badP === 0) {
      showNotice("Loại hàng dạt đang có số lượng sản lượng nhưng chưa được điền đơn giá.", true);
      return;
    }

    setRecords((prev) => {
      // Find a record with the same date and product where price does NOT conflict.
      // E.g., if both have goodQty > 0, they must have the same goodPrice.
      // If both have badQty > 0, they must have the same badPrice.
      const existingIdx = prev.findIndex((r) => {
        const isSameProductAndDate = r.dateTime === formDate && r.product.toLowerCase() === formProduct.toLowerCase();
        if (!isSameProductAndDate) return false;
        
        const goodPriceConflicted = r.goodQty > 0 && goodQ > 0 && r.goodPrice !== goodP;
        const badPriceConflicted = r.badQty > 0 && badQ > 0 && r.badPrice !== badP;
        
        return !goodPriceConflicted && !badPriceConflicted;
      });

      if (existingIdx !== -1) {
        const updated = [...prev];
        const ext = updated[existingIdx];
        const combinedGoodQty = ext.goodQty + goodQ;
        const combinedBadQty = ext.badQty + badQ;

        updated[existingIdx] = {
          ...ext,
          goodQty: combinedGoodQty,
          badQty: combinedBadQty,
          goodPrice: combinedGoodQty > 0 ? (ext.goodQty > 0 ? ext.goodPrice : goodP) : 0,
          badPrice: combinedBadQty > 0 ? (ext.badQty > 0 ? ext.badPrice : badP) : 0,
        };
        return updated;
      } else {
        const newRecord: HarvestRecord = {
          id: crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).substring(2, 9),
          dateTime: formDate,
          product: formProduct,
          goodQty: goodQ,
          badQty: badQ,
          goodPrice: goodP,
          badPrice: badP
        };
        return [newRecord, ...prev];
      }
    });

    setFormGoodQty("0");
    setFormBadQty("0");
    showNotice(`Đã lưu thành công ghi chép thu hoạch cho loại nông sản: ${formProduct}.`, false);
  };

  const startEditing = (record: HarvestRecord) => {
    setEditingRecordId(record.id);
    setEditProduct(record.product);
    setEditGoodQty(record.goodQty.toString());
    setEditGoodPrice(record.goodPrice.toString());
    setEditBadQty(record.badQty.toString());
    setEditBadPrice(record.badPrice.toString());
  };

  const saveEditing = (recordId: string, dateTime: string) => {
    const goodQ = parseFloat(editGoodQty) || 0;
    const goodP = parseFloat(editGoodPrice) || 0;
    const badQ = parseFloat(editBadQty) || 0;
    const badP = parseFloat(editBadPrice) || 0;

    if (!editProduct) {
      showNotice("Lỗi: Bạn loại bỏ nông sản, trường này không được để rỗng.", true);
      return;
    }

    if (goodQ < 0 || goodP < 0 || badQ < 0 || badP < 0) {
      showNotice("Lỗi: Số lượng sản phẩm hay đơn giá không thể nhỏ hơn 0.", true);
      return;
    }

    if (goodQ === 0 && badQ === 0 && goodP === 0 && badP === 0) {
      showNotice("Ghi chép chỉnh sửa đang để rỗng dữ liệu.", true);
      return;
    }

    if (goodQ === 0 && badQ === 0) {
      showNotice("Bạn hãy điền số lượng tương ứng cho giá sản phẩm vừa điền.", true);
      return;
    }

    if (goodQ > 0 && goodP === 0) {
      showNotice("Hàng ngon đang có sản lượng nhưng thiếu đơn giá.", true);
      return;
    }

    if (badQ > 0 && badP === 0) {
      showNotice("Hàng dạt loại 2 đang có sản lượng nhưng thiếu đơn giá.", true);
      return;
    }

    setRecords((prev) => {
      // Find a record with the same date/product that isn't the one being edited,
      // and whose prices do NOT conflict (meaning they can be merged).
      const existingIdx = prev.findIndex((r) => {
        const isMatch = r.id !== recordId && r.dateTime === dateTime && r.product.toLowerCase() === editProduct.toLowerCase();
        if (!isMatch) return false;

        const goodPriceConflicted = r.goodQty > 0 && goodQ > 0 && r.goodPrice !== goodP;
        const badPriceConflicted = r.badQty > 0 && badQ > 0 && r.badPrice !== badP;

        return !goodPriceConflicted && !badPriceConflicted;
      });

      if (existingIdx !== -1) {
        const updated = [...prev];
        const ext = updated[existingIdx];
        const combinedGoodQty = ext.goodQty + goodQ;
        const combinedBadQty = ext.badQty + badQ;

        updated[existingIdx] = {
          ...ext,
          goodQty: combinedGoodQty,
          badQty: combinedBadQty,
          goodPrice: combinedGoodQty > 0 ? (ext.goodQty > 0 ? ext.goodPrice : goodP) : 0,
          badPrice: combinedBadQty > 0 ? (ext.badQty > 0 ? ext.badPrice : badP) : 0,
        };
        return updated.filter((r) => r.id !== recordId);
      } else {
        return prev.map((r) =>
          r.id === recordId
            ? {
                ...r,
                dateTime,
                product: editProduct,
                goodQty: goodQ,
                goodPrice: goodP,
                badQty: badQ,
                badPrice: badP
              }
            : r
        );
      }
    });
    setEditingRecordId(null);
    showNotice("Đã cập nhật chỉnh sửa dòng ghi chép lịch sử thành công.", false);
  };

  const handleAddProduct = () => {
    const name = newProductNameInput.trim();
    if (!name) {
      showNotice("Vui lòng điền tên loại nông sản cây trồng muốn thêm vào vụ mùa.", true);
      return;
    }
    if (products.some((p) => p.name.toLowerCase() === name.toLowerCase())) {
      showNotice("Tên loại nông sản gieo trồng này đã chứa trong hệ thống.", true);
      return;
    }

    setProducts((prev) => [...prev, { name }]);
    setNewProductNameInput("");
    showNotice(`Đã thêm mới danh mục nông sản gieo trồng: ${name}.`, false);
  };

  const confirmDeleteRecord = () => {
    if (pendingDeleteRecord) {
      setRecords((prev) => prev.filter((r) => r.id !== pendingDeleteRecord.id));
      showNotice("Đã loại bỏ dòng ghi chép thu hoạch ra khỏi lịch sử.", false);
      setPendingDeleteRecord(null);
    }
  };

  const confirmDeleteProduct = () => {
    if (pendingDeleteProduct) {
      setProducts((prev) => prev.filter((p) => p.name !== pendingDeleteProduct.name));
      if (formProduct === pendingDeleteProduct.name) {
        setFormProduct("");
      }
      showNotice(`Đã xóa bỏ loại nông sản: ${pendingDeleteProduct.name}.`, false);
      setPendingDeleteProduct(null);
    }
  };

  const handleResetSeason = () => {
    setRecords([]);
    setProducts([
      { name: "Mướp" },
      { name: "Đậu đũa" },
      { name: "Bầu" },
      { name: "Bí đao" }
    ]);
    setCollapsedDates({});
    setShowResetConfirm(false);
    showNotice("Vụ mùa đã được làm mới hoàn toàn! Tất cả danh mục và lịch sử được đưa về mặc định.", false);
  };

  // Modern JSON import & export backup of DThanh Farm
  const downloadBackupFile = () => {
    try {
      const payload = {
        exportedAt: new Date().toISOString(),
        products: products.map((p) => p.name),
        records: records.map((r) => ({
          id: r.id,
          dateTime: r.dateTime,
          product: r.product,
          goodQty: r.goodQty,
          badQty: r.badQty,
          goodPrice: r.goodPrice,
          badPrice: r.badPrice
        }))
      };

      const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(payload, null, 2));
      const downloadAnchor = document.createElement("a");
      downloadAnchor.setAttribute("href", dataStr);
      downloadAnchor.setAttribute("download", "dthanh_farm_backup.json");
      document.body.appendChild(downloadAnchor);
      downloadAnchor.click();
      downloadAnchor.remove();
      showNotice("Đã xuất và tải về tập tin sao lưu dữ liệu (.json) thành công.", false);
    } catch (e) {
      showNotice("Lỗi: Không thể xuất dữ liệu sao lưu thành công.", true);
    }
  };

  const handleImportBackupFile = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (e) => {
      try {
        const text = e.target?.result as string;
        const payload = JSON.parse(text);

        if (payload && Array.isArray(payload.products) && Array.isArray(payload.records)) {
          const importedProductsList = payload.products.map((name: string) => ({ name }));
          setProducts(importedProductsList);

          const importedRecordsList = payload.records.map((r: any) => ({
            id: r.id || crypto.randomUUID(),
            dateTime: r.dateTime,
            product: r.product,
            goodQty: Number(r.goodQty) || 0,
            badQty: Number(r.badQty) || 0,
            goodPrice: Number(r.goodPrice) || 0,
            badPrice: Number(r.badPrice) || 0
          }));
          setRecords(importedRecordsList);

          showNotice("Dữ liệu sao lưu đã được khôi phục thành công vào ứng dụng.", false);
        } else {
          showNotice("Lỗi: Định dạng cấu trúc tập tin sao lưu không chính xác.", true);
        }
      } catch (err) {
        showNotice("Lỗi: Không thể phân tích tập tin sao lưu được chọn.", true);
      }
    };
    reader.readAsText(file);
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  // Adjust numeric steppers by unit values
  const handleStepValue = (
    currentVal: string,
    setVal: (v: string) => void,
    step: number,
    isIncrement: boolean,
    min: number = 0
  ) => {
    const currentNum = parseFloat(currentVal) || 0;
    const nextNum = isIncrement ? currentNum + step : Math.max(min, currentNum - step);
    setVal(Number((nextNum % 1 === 0 ? nextNum : nextNum.toFixed(2))).toString());
  };

  // Group historic records by date Str
  const groupedRecords = records.reduce((groups, record) => {
    const date = record.dateTime;
    if (!groups[date]) {
      groups[date] = [];
    }
    groups[date].push(record);
    return groups;
  }, {} as Record<string, HarvestRecord[]>);

  const sortedDates = Object.keys(groupedRecords).sort((a, b) => b.localeCompare(a));

  // Summary statistics calculated values
  const totalRevenueAll = records.reduce((sum, r) => sum + calcRevenue(r), 0);

  const summarizedStats = products.map((p) => {
    const matchedRecords = records.filter((r) => r.product === p.name);
    const goodSum = matchedRecords.reduce((sum, r) => sum + r.goodQty, 0);
    const badSum = matchedRecords.reduce((sum, r) => sum + r.badQty, 0);
    const goodRev = matchedRecords.reduce((sum, r) => sum + (r.goodQty * r.goodPrice), 0);
    const badRev = matchedRecords.reduce((sum, r) => sum + (r.badQty * r.badPrice), 0);
    const revSum = matchedRecords.reduce((sum, r) => sum + calcRevenue(r), 0);
    return {
      product: p.name,
      goodQty: goodSum,
      badQty: badSum,
      goodRevenue: goodRev,
      badRevenue: badRev,
      revenue: revSum
    };
  }).sort((a, b) => b.revenue - a.revenue);

  return (
    <div className="min-h-screen bg-[#F3F5EF] flex flex-col items-center pb-24 font-sans text-gray-800 antialiased selection:bg-[#B7DE97] selection:text-[#1B5E20]">
      {/* Container Box */}
      <div className="w-full max-w-lg lg:max-w-6xl px-4 pt-6 space-y-4">
        
        {/* Brand Header with golden woven bamboo accent border */}
        <div className="w-full bg-[#1B3E1C] text-[#FFF4D6] rounded-[24px] p-4 shadow-sm border-2 border-[#D4A373] flex items-center space-x-4 relative overflow-hidden">
          <div className="absolute top-0 right-0 w-32 h-32 bg-[#2E7D32]/20 rounded-full blur-2xl animate-pulse pointer-events-none"></div>
          
          {/* Logo container utilizing optimized app image with scale framing */}
          <div className="w-20 h-20 rounded-[18px] overflow-hidden border-[1.5px] border-[#FFF4D6] shadow-sm flex items-center justify-center bg-[#214D3A] shrink-0 text-white relative transition-transform duration-300 hover:scale-105">
            <img 
              src={logoUrl} 
              alt="DThanh Farm Logo" 
              referrerPolicy="no-referrer"
              className="w-full h-full object-cover absolute inset-0 z-10 p-1 bg-[#1B3E1C] rounded-[18px]" 
              onError={(e) => {
                // If path is unavailable, display Sprout visual fallback
                e.currentTarget.style.display = 'none';
              }}
            />
            <Sprout className="w-12 h-12 text-[#EFFFD0] animate-bounce" />
          </div>
          
          <div className="flex-1">
            <h1 className="font-serif font-black text-2xl tracking-wide text-white">DThanh Farm</h1>
            <p className="font-bold text-sm text-[#EFFFD0] mt-0.5">Sổ tay nông sản</p>
          </div>
        </div>

        {/* Database import-export management & Season Reset control panels */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
          <label className="flex items-center justify-center space-x-2 bg-white hover:bg-gray-50 border border-[#B7DE97] text-[#1B5E20] font-bold text-xs py-3.5 px-3 rounded-[16px] cursor-pointer shadow-sm transition-all duration-200">
            <Upload className="w-4 h-4 text-emerald-600" />
            <span>Nhập tệp dữ liệu</span>
            <input
              type="file"
              accept=".json"
              ref={fileInputRef}
              onChange={handleImportBackupFile}
              className="hidden"
            />
          </label>

          <button
            onClick={downloadBackupFile}
            className="flex items-center justify-center space-x-2 bg-white hover:bg-gray-50 border border-[#B7DE97] text-[#1B5E20] font-bold text-xs py-3.5 px-3 rounded-[16px] shadow-sm transition-all duration-200"
          >
            <Download className="w-4 h-4 text-emerald-600" />
            <span>Xuất tệp dữ liệu</span>
          </button>

          {/* New Reset Season custom button representing QR/Zalo replacement */}
          <button
            onClick={() => setShowResetConfirm(true)}
            className="flex items-center justify-center space-x-2 bg-red-50 hover:bg-red-100 text-[#C62828] font-black text-xs py-3.5 px-3 rounded-[16px] border border-red-150 shadow-sm transition-all duration-200"
          >
            <RefreshCw className="w-4 h-4 animate-spin-slow text-red-600" />
            <span>Làm mới vụ mùa (Xoá hết)</span>
          </button>
        </div>

        {/* Dynamic Responsive Workspace Grid */}
        <div className="pb-8 grid grid-cols-1 lg:grid-cols-12 lg:gap-6 gap-4 items-start">
          
          {/* TAB 1: INPUT NEW HARVEST */}
          {(activeTab === "input" || true) && (
            <div className={`${activeTab === "input" ? "block" : "hidden lg:block"} lg:col-span-5 w-full space-y-4`}>
              <motion.div
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                className="bg-white rounded-[24px] border border-[#DFE7D7] p-4 shadow-sm space-y-4"
              >
                {/* Header Section Title */}
                <div className="relative pb-3 border-b border-dashed border-[#B7DE97] flex items-center space-x-3">
                  <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-[#D9F7C8] to-[#EFFFD0] flex items-center justify-center shrink-0">
                    <Plus className="w-5 h-5 text-[#1B5E20]" />
                  </div>
                  <div>
                    <h3 className="font-black text-base text-gray-900 leading-tight">1. Nhập ghi chép mới</h3>
                    <p className="text-[11px] text-gray-400">Chọn loại nông sản, số lượng thu hoạch và đơn giá tương ứng</p>
                  </div>
                </div>

                {/* Solar calendar & Lunar Day selector */}
                <div className="flex items-center justify-between p-3 rounded-[16px] border border-[#E2E8DC] bg-[#FBFCF8] relative">
                  <div className="flex-1 flex flex-col justify-center">
                    <span className="text-[9px] font-black text-gray-400 uppercase tracking-wider">Ngày dương lịch</span>
                    <div className="flex items-center mt-1 relative">
                      <Calendar className="w-4 h-4 text-emerald-600 mr-1.5 absolute left-0 pointer-events-none" />
                      <input
                        type="date"
                        value={formDate}
                        onChange={(e) => setFormDate(e.target.value)}
                        className="font-black text-sm bg-transparent border-none outline-none focus:ring-0 text-gray-900 w-full pl-6 cursor-pointer"
                      />
                    </div>
                  </div>

                  <div className="shrink-0 bg-[#2D6A4F] text-[#FBFBF9] font-black text-xs px-3 py-1.5 rounded-xl shadow-xs">
                    {lunarTextFromDateTime(formDate) || "Lịch Âm"}
                  </div>
                </div>

                {/* Crop Product selector & Category settings drawer */}
                <div className="p-3.5 rounded-[16px] border border-[#E2E8DC] bg-[#FBFCF8] space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-[9px] font-black text-gray-400 uppercase tracking-wider">Loại nông sản gieo trồng</span>
                    
                    <button
                      onClick={() => setShowProductManager(!showProductManager)}
                      className="flex items-center space-x-1 bg-[#E6F9D8] text-[#2E7D32] hover:bg-[#D5F5C1] text-[10px] font-black px-2.5 py-1 rounded-full transition-all duration-150 shadow-xs"
                    >
                      <Settings className="w-3 h-3" />
                      <span>Cài đặt nông sản</span>
                    </button>
                  </div>

                  {/* Inline Product Manager panel */}
                  <AnimatePresence>
                    {showProductManager && (
                      <motion.div
                        initial={{ opacity: 0, height: 0 }}
                        animate={{ opacity: 1, height: "auto" }}
                        exit={{ opacity: 0, height: 0 }}
                        className="overflow-hidden bg-[#E6F9D8]/50 rounded-[12px] p-2.5 text-xs space-y-2 border border-[#C8E6C9]"
                      >
                        <h4 className="font-bold text-[#1B5E20] text-xs">Thêm / Bỏ loại nông sản:</h4>
                        <div className="flex items-center space-x-2">
                          <input
                            type="text"
                            placeholder="Nhập tên loại nông sản..."
                            value={newProductNameInput}
                            onChange={(e) => setNewProductNameInput(e.target.value)}
                            className="flex-1 bg-white border border-[#B7DE97] focus:border-[#2E7D32] h-9 px-2 rounded-lg text-xs outline-none"
                          />
                          <button
                            onClick={handleAddProduct}
                            className="bg-[#2D6A4F] hover:bg-[#1B4332] text-white font-extrabold px-3 h-9 rounded-lg transition-all duration-150 shadow-sm shrink-0"
                          >
                            Thêm mới
                          </button>
                        </div>

                        {/* Crop list options tagged as pills with individual action indicators */}
                        <div className="flex flex-wrap gap-1.5 pt-1.5">
                          {products.map((p) => (
                            <div
                              key={p.name}
                              className="bg-white border border-[#B7DE97] pl-2.5 pr-1 py-0.5 rounded-full flex items-center space-x-1 text-[11px] text-[#1B5E20] font-black shadow-xs"
                            >
                              <span>{p.name}</span>
                              <button
                                onClick={() => {
                                  // Verify product is not already used inside existing historical harvest records!
                                  const hasInHistory = records.some((r) => r.product.toLowerCase() === p.name.toLowerCase());
                                  if (hasInHistory) {
                                    showNotice(`Không cho phép xóa nông sản "${p.name}" vì loại nông sản này hiện đã chứa ghi chép trong Lịch sử thu hoạch!`, true);
                                    return;
                                  }
                                  setPendingDeleteProduct(p);
                                }}
                                className="w-5 h-5 rounded-full hover:bg-red-50 flex items-center justify-center text-red-500 hover:text-red-700 transition"
                              >
                                <X className="w-3 h-3" />
                              </button>
                            </div>
                          ))}
                        </div>
                      </motion.div>
                    )}
                  </AnimatePresence>

                  {/* Combobox selections */}
                  <select
                    value={formProduct}
                    onChange={(e) => setFormProduct(e.target.value)}
                    className="w-full bg-white border border-[#E2E8DC] focus:border-[#74B35D] text-sm font-black text-gray-800 rounded-xl px-3 py-2.5 outline-none cursor-pointer focus:ring-0"
                  >
                    {products.length === 0 ? (
                      <option value="">(Chưa cấu hình loại nông sản)</option>
                    ) : (
                      products.map((p) => (
                        <option key={p.name} value={p.name}>
                          {p.name}
                        </option>
                      ))
                    )}
                  </select>
                </div>

                {/* Segment: Good products prices inputs */}
                <div className="rounded-[18px] border border-emerald-600/15 bg-[#E8F5E9] p-3.5 space-y-3">
                  <div className="flex items-center justify-between">
                    <h4 className="font-extrabold text-sm text-[#1B5E20]">Hàng ngon</h4>
                    <span className="text-[9px] font-bold bg-white text-[#1B5E20] px-2 py-0.5 rounded-md shadow-xs">
                      Sản lượng ±1kg · Đơn giá ±500đ
                    </span>
                  </div>

                  <div className="grid grid-cols-2 gap-3">
                    <div className="space-y-1">
                      <span className="text-[9px] font-black text-gray-500 uppercase tracking-wide">Số lượng (kg)</span>
                      <div className="bg-white border border-[#E2E8DC] rounded-xl flex items-center h-11 overflow-hidden shadow-xs">
                        <button
                          onClick={() => handleStepValue(formGoodQty, setFormGoodQty, 1, false)}
                          className="w-9 h-full hover:bg-gray-50 flex items-center justify-center text-[#4F8C3F] font-bold transition"
                        >
                          <ChevronDown className="w-4 h-4" />
                        </button>
                        <input
                          type="text"
                          value={formGoodQty}
                          onChange={(e) => {
                            const val = e.target.value;
                            if (val === "" || !isNaN(Number(val))) setFormGoodQty(val);
                          }}
                          className="flex-1 text-center font-bold text-sm outline-none border-none focus:ring-0 w-full"
                        />
                        <button
                          onClick={() => handleStepValue(formGoodQty, setFormGoodQty, 1, true)}
                          className="w-9 h-full hover:bg-gray-50 flex items-center justify-center text-[#4F8C3F] font-bold transition"
                        >
                          <ChevronUp className="w-4 h-4" />
                        </button>
                      </div>
                    </div>

                    <div className="space-y-1">
                      <span className="text-[9px] font-black text-gray-500 uppercase tracking-wide">Đơn giá (đọc đ/kg)</span>
                      <div className="bg-white border border-[#E2E8DC] rounded-xl flex items-center h-11 overflow-hidden shadow-xs">
                        <button
                          onClick={() => handleStepValue(formGoodPrice, setFormGoodPrice, 500, false)}
                          className="w-9 h-full hover:bg-gray-50 flex items-center justify-center text-[#4F8C3F] font-bold transition"
                        >
                          <ChevronDown className="w-4 h-4" />
                        </button>
                        <input
                          type="text"
                          value={formGoodPrice}
                          onChange={(e) => {
                            const val = e.target.value;
                            if (val === "" || !isNaN(Number(val))) setFormGoodPrice(val);
                          }}
                          className="flex-1 text-center font-bold text-sm outline-none border-none focus:ring-0 w-full"
                        />
                        <button
                          onClick={() => handleStepValue(formGoodPrice, setFormGoodPrice, 500, true)}
                          className="w-9 h-full hover:bg-gray-50 flex items-center justify-center text-[#4F8C3F] font-bold transition"
                        >
                          <ChevronUp className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                  </div>
                </div>

                {/* Segment: Leftovers dạt harvest categories */}
                <div className="rounded-[18px] border border-orange-500/15 bg-[#FFF3E0] p-3.5 space-y-3">
                  <div className="flex items-center justify-between">
                    <h4 className="font-extrabold text-sm text-[#E65100]">Hàng dạt</h4>
                    <span className="text-[9px] font-bold bg-white text-[#E65100] px-2 py-0.5 rounded-md shadow-xs">
                      Sản lượng ±1kg · Đơn giá ±500đ
                    </span>
                  </div>

                  <div className="grid grid-cols-2 gap-3">
                    <div className="space-y-1">
                      <span className="text-[9px] font-black text-gray-500 uppercase tracking-wide">Số lượng (kg)</span>
                      <div className="bg-white border border-[#E2E8DC] rounded-xl flex items-center h-11 overflow-hidden shadow-xs">
                        <button
                          onClick={() => handleStepValue(formBadQty, setFormBadQty, 1, false)}
                          className="w-9 h-full hover:bg-gray-50 flex items-center justify-center text-[#E65100] font-bold transition"
                        >
                          <ChevronDown className="w-4 h-4" />
                        </button>
                        <input
                          type="text"
                          value={formBadQty}
                          onChange={(e) => {
                            const val = e.target.value;
                            if (val === "" || !isNaN(Number(val))) setFormBadQty(val);
                          }}
                          className="flex-1 text-center font-bold text-sm outline-none border-none focus:ring-0 w-full"
                        />
                        <button
                          onClick={() => handleStepValue(formBadQty, setFormBadQty, 1, true)}
                          className="w-9 h-full hover:bg-gray-50 flex items-center justify-center text-[#E65100] font-bold transition"
                        >
                          <ChevronUp className="w-4 h-4" />
                        </button>
                      </div>
                    </div>

                    <div className="space-y-1">
                      <span className="text-[9px] font-black text-gray-500 uppercase tracking-wide">Đơn giá (đọc đ/kg)</span>
                      <div className="bg-white border border-[#E2E8DC] rounded-xl flex items-center h-11 overflow-hidden shadow-xs">
                        <button
                          onClick={() => handleStepValue(formBadPrice, setFormBadPrice, 500, false)}
                          className="w-9 h-full hover:bg-gray-50 flex items-center justify-center text-[#E65100] font-bold transition"
                        >
                          <ChevronDown className="w-4 h-4" />
                        </button>
                        <input
                          type="text"
                          value={formBadPrice}
                          onChange={(e) => {
                            const val = e.target.value;
                            if (val === "" || !isNaN(Number(val))) setFormBadPrice(val);
                          }}
                          className="flex-1 text-center font-bold text-sm outline-none border-none focus:ring-0 w-full"
                        />
                        <button
                          onClick={() => handleStepValue(formBadPrice, setFormBadPrice, 500, true)}
                          className="w-9 h-full hover:bg-gray-50 flex items-center justify-center text-[#E65100] font-bold transition"
                        >
                          <ChevronUp className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                  </div>
                </div>

                {/* Estimate sum total for current record insertion */}
                <div className="bg-gradient-to-r from-[#214D3A] to-[#2D6A4F] rounded-[16px] p-3.5 flex items-center justify-between text-white shadow-md">
                  <div>
                    <span className="text-[10px] font-bold text-[#E8F5E9] tracking-wider block uppercase">DOANH THU GHI CHÉP</span>
                    <p className="text-xl font-black mt-0.5">
                      {formatVnd(
                        (parseFloat(formGoodQty) || 0) * (parseFloat(formGoodPrice) || 0) +
                          (parseFloat(formBadQty) || 0) * (parseFloat(formBadPrice) || 0)
                      )}
                    </p>
                  </div>

                  <button
                    onClick={handleAddHarvestRecord}
                    className="bg-white text-[#1B3E1C] hover:bg-emerald-50 active:scale-95 font-black text-xs px-4 py-3 rounded-xl transition duration-150 shadow-xs shrink-0"
                  >
                    Lưu ghi chép
                  </button>
                </div>
              </motion.div>
            </div>
          )}

          {/* TAB 2 & 3 UNIFIED RIGHT COLUMN: HISTORICAL HARVEST RECONCILIATIONS AND VISUAL SUMMARIES */}
          {(activeTab === "history" || activeTab === "input" || activeTab === "summary") && (
            <div className={`${activeTab === "input" ? "hidden lg:block" : "block"} lg:col-span-7 w-full space-y-4`}>
              
              {/* Desktop Subtab Switchers */}
              <div className="hidden lg:flex items-center space-x-2 bg-[#E6F9D8]/40 p-1.5 rounded-[18px] border border-[#C8E6C9] shadow-xs">
                <button
                  onClick={() => {
                    setActiveTab("history");
                    setShowProductManager(false);
                  }}
                  className={`flex-1 py-2.5 rounded-xl font-black text-xs transition duration-200 flex items-center justify-center space-x-1.5 ${
                    activeTab !== "summary"
                      ? "bg-[#214D3A] text-white shadow-xs"
                      : "text-[#55604C] hover:bg-white/50"
                  }`}
                >
                  <History className="w-3.5 h-3.5" />
                  <span>Sổ tay Lịch sử</span>
                </button>
                <button
                  onClick={() => {
                    setActiveTab("summary");
                    setShowProductManager(false);
                  }}
                  className={`flex-1 py-2.5 rounded-xl font-black text-xs transition duration-200 flex items-center justify-center space-x-1.5 ${
                    activeTab === "summary"
                      ? "bg-[#214D3A] text-white shadow-xs"
                      : "text-[#55604C] hover:bg-white/50"
                  }`}
                >
                  <FileSpreadsheet className="w-3.5 h-3.5" />
                  <span>Tổng kết Vụ mùa</span>
                </button>
              </div>

              {activeTab !== "summary" ? (
                /* SỔ TAY LỊCH SỬ VIEW */
                <motion.div
                  key="history-view"
                  initial={{ opacity: 0, y: 12 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="space-y-4"
                >
                  <div className="relative pb-3 border-b border-dashed border-[#B7DE97] flex items-center space-x-3">
                    <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-[#D9F7C8] to-[#EFFFD0] flex items-center justify-center shrink-0">
                      <History className="w-5 h-5 text-[#1B5E20]" />
                    </div>
                    <div>
                      <h3 className="font-black text-base text-gray-900 leading-tight">2. Sổ tay lịch sử thu hoạch</h3>
                      <p className="text-[11px] text-gray-400">Xem trực quan các ngày thu hoạch. Nhấn dòng Ngày để ẩn/hiện bảng chỉnh sửa chi tiết.</p>
                    </div>
                  </div>

                  {records.length === 0 ? (
                    <div className="bg-white border border-[#DFE7D7] rounded-[24px] p-10 text-center flex flex-col items-center justify-center space-y-3">
                      <Database className="w-12 h-12 text-gray-200" />
                      <p className="font-bold text-gray-400 text-sm">Chưa có dòng dữ liệu lịch sử nào trong vụ mùa này.</p>
                      <button
                        onClick={() => setActiveTab("input")}
                        className="text-xs bg-[#2D6A4F] hover:bg-[#1B4332] text-white font-bold py-2 px-4 rounded-[12px] shadow-sm transition"
                      >
                        Nhập dòng mới ngay
                      </button>
                    </div>
                  ) : (
                    <div className="space-y-3">
                      {sortedDates.map((dateStr) => {
                        const dateRecords = groupedRecords[dateStr];
                        const daysTotalRevenue = dateRecords.reduce((sum, r) => sum + calcRevenue(r), 0);
                        const rawLunar = lunarTextFromDateTime(dateStr) || "";

                        return (
                          <div key={dateStr} className="bg-white rounded-xl border border-[#DEE5D9] overflow-hidden shadow-2xs transition-all duration-200">
                            
                            {/* Static Date Row Indicator (Always Open) */}
                            <div
                              className="w-full bg-[#E8F5E9]/90 border-b border-[#DEE5D9] text-[#1B3E1C] py-1.5 px-2.5 flex items-center justify-between gap-2 select-none"
                            >
                              <div className="flex items-center gap-1.5">
                                <span className="font-extrabold text-xs text-gray-900 bg-white/60 px-1.5 py-0.5 rounded border border-[#DFE7D7]">
                                  {formatDisplayDate(dateStr)}
                                </span>
                                {rawLunar && (
                                  <span className="bg-[#1B3E1C] text-[#EFFFD0] font-bold text-[9px] px-1.5 py-0.5 rounded">
                                    {rawLunar}
                                  </span>
                                )}
                              </div>
                              <span className="text-[#1B5E20] font-black text-[11px] bg-white border border-[#CEDEC8] px-1.5 py-0.5 rounded shadow-3xs shrink-0">
                                Thu nhập: {formatVnd(daysTotalRevenue)}
                              </span>
                            </div>

                            {/* Always-visible daily crop records summary - auto-grows when adding more kinds of crops */}
                            <div className="p-1 space-y-1 bg-[#FCFDFB]">
                              {dateRecords.map((rec) => {
                                const isRowEditing = editingRecordId === rec.id;
                                const colorAccent = getProductColorAccent(rec.product);

                                return (
                                  <div key={rec.id} className="relative bg-white border border-[#E2E8DC] rounded-md p-1.5 shadow-3xs hover:border-[#CBD6C3] transition duration-150">
                                    <div 
                                      className="absolute left-0 top-0 bottom-0 w-1 rounded-l"
                                      style={{ backgroundColor: colorAccent }}
                                    ></div>
                                    
                                    {isRowEditing ? (
                                      // Row Editing State (Slightly narrower form layout)
                                      <div className="space-y-2 pt-0.5 pb-1 pl-1.5">
                                        <div className="space-y-1">
                                          <span className="text-[9px] font-black text-gray-400 block uppercase">Nông sản</span>
                                          <select
                                            value={editProduct}
                                            onChange={(e) => setEditProduct(e.target.value)}
                                            className="w-full bg-white border border-[#E2E8DC] rounded-lg px-2 py-1 text-xs font-bold outline-none"
                                          >
                                            {products.map((p) => (
                                              <option key={p.name} value={p.name}>
                                                {p.name}
                                              </option>
                                            ))}
                                          </select>
                                        </div>

                                        <div className="grid grid-cols-2 gap-1.5">
                                          <div className="space-y-0.5">
                                            <span className="text-[9px] font-bold text-[#1B5E20]">Ngon (kg)</span>
                                            <div className="border border-[#E2E8DC] rounded-md flex items-center h-7.5 bg-white overflow-hidden">
                                              <button
                                                onClick={() => handleStepValue(editGoodQty, setEditGoodQty, 1, false)}
                                                className="w-6 h-full hover:bg-gray-50 flex items-center justify-center text-[#4F8C3F] text-xs font-black"
                                              >
                                                -
                                              </button>
                                              <input
                                                type="text"
                                                value={editGoodQty}
                                                onChange={(e) => setEditGoodQty(e.target.value)}
                                                className="flex-1 text-center font-bold text-xs outline-none border-none focus:ring-0 w-full p-0"
                                              />
                                              <button
                                                onClick={() => handleStepValue(editGoodQty, setEditGoodQty, 1, true)}
                                                className="w-6 h-full hover:bg-gray-50 flex items-center justify-center text-[#4F8C3F] text-xs font-black"
                                              >
                                                +
                                              </button>
                                            </div>
                                          </div>

                                          <div className="space-y-0.5">
                                            <span className="text-[9px] font-bold text-[#1B5E20]">Đơn giá (đ)</span>
                                            <div className="border border-[#E2E8DC] rounded-md flex items-center h-7.5 bg-white overflow-hidden">
                                              <button
                                                onClick={() => handleStepValue(editGoodPrice, setEditGoodPrice, 500, false)}
                                                className="w-6 h-full hover:bg-gray-50 flex items-center justify-center text-[#4F8C3F] text-xs font-black"
                                              >
                                                -
                                              </button>
                                              <input
                                                type="text"
                                                value={editGoodPrice}
                                                onChange={(e) => setEditGoodPrice(e.target.value)}
                                                className="flex-1 text-center font-bold text-xs outline-none border-none focus:ring-0 w-full p-0"
                                              />
                                              <button
                                                onClick={() => handleStepValue(editGoodPrice, setEditGoodPrice, 500, true)}
                                                className="w-6 h-full hover:bg-gray-50 flex items-center justify-center text-[#4F8C3F] text-xs font-black"
                                              >
                                                +
                                              </button>
                                            </div>
                                          </div>

                                          <div className="space-y-0.5">
                                            <span className="text-[9px] font-bold text-[#E65100]">Dạt (kg)</span>
                                            <div className="border border-[#E2E8DC] rounded-md flex items-center h-7.5 bg-white overflow-hidden">
                                              <button
                                                onClick={() => handleStepValue(editBadQty, setEditBadQty, 1, false)}
                                                className="w-6 h-full hover:bg-gray-50 flex items-center justify-center text-[#E65100] text-xs font-black"
                                              >
                                                -
                                              </button>
                                              <input
                                                type="text"
                                                value={editBadQty}
                                                onChange={(e) => setEditBadQty(e.target.value)}
                                                className="flex-1 text-center font-bold text-xs outline-none border-none focus:ring-0 w-full p-0"
                                              />
                                              <button
                                                onClick={() => handleStepValue(editBadQty, setEditBadQty, 1, true)}
                                                className="w-6 h-full hover:bg-gray-50 flex items-center justify-center text-[#E65100] text-xs font-black"
                                              >
                                                +
                                              </button>
                                            </div>
                                          </div>

                                          <div className="space-y-0.5">
                                            <span className="text-[9px] font-bold text-[#E65100]">Đơn giá (đ)</span>
                                            <div className="border border-[#E2E8DC] rounded-md flex items-center h-7.5 bg-white overflow-hidden">
                                              <button
                                                onClick={() => handleStepValue(editBadPrice, setEditBadPrice, 500, false)}
                                                className="w-6 h-full hover:bg-gray-50 flex items-center justify-center text-[#E65100] text-xs font-black"
                                              >
                                                -
                                              </button>
                                              <input
                                                type="text"
                                                value={editBadPrice}
                                                onChange={(e) => setEditBadPrice(e.target.value)}
                                                className="flex-1 text-center font-bold text-xs outline-none border-none focus:ring-0 w-full p-0"
                                              />
                                              <button
                                                onClick={() => handleStepValue(editBadPrice, setEditBadPrice, 500, true)}
                                                className="w-6 h-full hover:bg-gray-50 flex items-center justify-center text-[#E65100] text-xs font-black"
                                              >
                                                +
                                              </button>
                                            </div>
                                          </div>
                                        </div>

                                        <div className="flex space-x-1.5 pt-1.5 border-t border-dashed border-gray-100">
                                          <button
                                            onClick={() => saveEditing(rec.id, rec.dateTime)}
                                            className="flex-1 h-8 rounded bg-[#E2F0D9] text-[#1B5E20] flex items-center justify-center text-xs font-black transition gap-1"
                                          >
                                            <Check className="w-3 h-3" />
                                            Lưu lại
                                          </button>
                                          <button
                                            onClick={() => setEditingRecordId(null)}
                                            className="flex-1 h-8 rounded bg-gray-50 text-gray-500 flex items-center justify-center text-xs font-bold transition"
                                          >
                                            Quay lại
                                          </button>
                                        </div>
                                      </div>
                                    ) : (
                                      // Elegant hyper-compact static list row
                                      <div className="pl-1.5 space-y-0.5">
                                        {/* Upper row: Name & Sửa/Xóa Admin Controls */}
                                        <div className="flex items-center justify-between">
                                          <h4 className="font-extrabold text-[12.5px]" style={{ color: colorAccent }}>
                                            {rec.product}
                                          </h4>
                                          <div className="flex items-center space-x-0.5 shrink-0">
                                            <button
                                              onClick={() => startEditing(rec)}
                                              className="p-1 rounded hover:bg-emerald-50 text-gray-400 hover:text-[#1B5E20] transition duration-100"
                                              title="Sửa"
                                            >
                                              <Edit2 className="w-3 h-3" />
                                            </button>
                                            <button
                                              onClick={() => setPendingDeleteRecord(rec)}
                                              className="p-1 rounded hover:bg-red-50 text-gray-400 hover:text-red-650 transition duration-100"
                                              title="Xóa"
                                            >
                                              <Trash2 className="w-3 h-3" />
                                            </button>
                                          </div>
                                        </div>

                                        {/* Lower row: packaged details horizontal layout with revenue on the right */}
                                        <div className="flex items-center justify-between text-[11px] text-gray-500 leading-none">
                                          <div className="flex flex-wrap items-center gap-x-2">
                                            {rec.goodQty > 0 && (
                                              <span className="inline-flex items-center space-x-0.5">
                                                <span className="w-1.5 h-1.5 rounded-full bg-emerald-600"></span>
                                                <span className="font-semibold text-gray-400">Ngon:</span>
                                                <span className="font-extrabold text-[#1B3E1C]">{rec.goodQty}kg</span>
                                                <span className="text-gray-300">x</span>
                                                <span className="text-gray-550 font-medium">{formatVnd(rec.goodPrice)}</span>
                                              </span>
                                            )}

                                            {rec.badQty > 0 && (
                                              <span className="inline-flex items-center space-x-0.5 border-l border-gray-200/60 pl-1.5">
                                                <span className="w-1.5 h-1.5 rounded-full bg-orange-400"></span>
                                                <span className="font-semibold text-gray-400">Dạt:</span>
                                                <span className="font-extrabold text-[#E65100]">{rec.badQty}kg</span>
                                                <span className="text-gray-300">x</span>
                                                <span className="text-gray-550 font-medium">{formatVnd(rec.badPrice)}</span>
                                              </span>
                                            )}
                                          </div>

                                          <span className="text-[11px] font-black text-[#1B5E20] bg-emerald-50/60 px-1 rounded-sm shrink-0">
                                            {formatVnd(calcRevenue(rec))}
                                          </span>
                                        </div>
                                      </div>
                                    )}
                                  </div>
                                );
                              })}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </motion.div>
              ) : (
                /* TỔNG KẾT VỤ MÙA VIEW */
                <motion.div
                  key="summary-view"
                  initial={{ opacity: 0, y: 12 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="space-y-4"
                >
                  <div className="relative pb-3 border-b border-dashed border-[#B7DE97] flex items-center space-x-3">
                    <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-[#D9F7C8] to-[#EFFFD0] flex items-center justify-center shrink-0">
                      <BarChart2 className="w-5 h-5 text-[#1B5E20]" />
                    </div>
                    <div>
                      <h3 className="font-black text-base text-gray-900 leading-tight">Tổng kết vụ mùa</h3>
                      <p className="text-[11px] text-gray-400">Số liệu doanh thu tổng hợp của toàn bộ các sản phẩm</p>
                    </div>
                  </div>

                  {/* Overall mega total harvest profit card */}
                  <div className="relative overflow-hidden bg-gradient-to-r from-[#214D3A] to-[#2D6A4F] rounded-[24px] p-6 text-[#FFF4D6] shadow-lg border border-[#D4A373]">
                    <div className="absolute top-0 right-0 w-44 h-44 bg-white/5 rounded-full blur-3xl pointer-events-none"></div>
                    <span className="text-[10px] font-bold text-[#E8F5E9] tracking-wider uppercase block">TỔNG DOANH THU TOÀN VỤ MÙA</span>
                    <p className="text-3xl font-black mt-1 leading-none text-white">{formatVnd(totalRevenueAll)}</p>
                    <div className="mt-3 text-[11px] text-[#EFFFD0] font-medium">
                      Danh thu của tất cả nông sản đã lưu
                    </div>
                  </div>

                  {records.length === 0 ? (
                    <div className="bg-white border border-[#DFE7D7] rounded-[24px] p-8 text-center text-gray-400 font-bold text-xs shadow-sm">
                      Chưa có hoạt động nào lưu trong lịch sử thời kì hiện tại.
                    </div>
                  ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-1 gap-4">
                      {summarizedStats.map((stat) => {
                        if (stat.goodQty === 0 && stat.badQty === 0 && stat.revenue === 0) return null;
                        const itemAccent = getProductColorAccent(stat.product);
                        const totalQty = stat.goodQty + stat.badQty;
                        const goodPercentage = totalQty > 0 ? Math.round((stat.goodQty / totalQty) * 100) : 0;

                        return (
                          <div
                            key={stat.product}
                            className="bg-white rounded-[22px] border border-[#DEE5D9] p-4.5 relative overflow-hidden shadow-sm space-y-4 transition-all duration-200 hover:shadow-md"
                          >
                            {/* Organic accent bar indicator */}
                            <div
                              className="absolute left-0 top-0 bottom-0 w-1.5"
                              style={{ backgroundColor: itemAccent }}
                            ></div>

                            {/* Header section with Name & Total Revenue badge next to it */}
                            <div className="pl-1.5 flex flex-wrap items-center justify-between gap-2 border-b border-dashed border-gray-100 pb-2.5">
                              <div className="flex items-center space-x-2 shrink-0">
                                <h4 className="font-extrabold text-base text-gray-900" style={{ color: itemAccent }}>
                                  {stat.product}
                                </h4>
                                <span className="text-[11px] font-black text-emerald-800 bg-emerald-50 border border-emerald-200/60 rounded-full px-2.5 py-0.5 font-sans">
                                  {formatVnd(stat.revenue)}
                                </span>
                              </div>
                            </div>

                            {/* Split layout representing Good vs Bad with individual weights & revenues */}
                            <div className="grid grid-cols-2 gap-3 pl-1.5">
                              {/* Good Crop Box */}
                              <div className="bg-[#E8F5E9]/70 p-3 rounded-2xl border border-[#A5D6A7]/30 flex flex-col justify-between min-h-[95px]">
                                <div>
                                  <p className="text-[9px] font-black text-[#1B5E20] uppercase tracking-wider">HÀNG NGON</p>
                                  <p className="font-black text-base text-[#2E7D32] mt-0.5">{stat.goodQty} kg</p>
                                </div>
                                <div className="border-t border-[#A5D6A7]/20 pt-2 mt-2">
                                  <span className="text-[9px] text-[#2E7D32]/80 font-bold block leading-tight">Doanh thu Ngon</span>
                                  <span className="font-extrabold text-xs text-[#1B5E20]">{formatVnd(stat.goodRevenue)}</span>
                                </div>
                              </div>

                              {/* Bad Crop Box */}
                              <div className="bg-[#FFF3E0]/70 p-3 rounded-2xl border border-[#FFCC80]/30 flex flex-col justify-between min-h-[95px]">
                                <div>
                                  <p className="text-[9px] font-black text-[#E65100] uppercase tracking-wider">HÀNG DẠT</p>
                                  <p className="font-black text-base text-[#E65100] mt-0.5">{stat.badQty} kg</p>
                                </div>
                                <div className="border-t border-[#FFCC80]/20 pt-2 mt-2">
                                  <span className="text-[9px] text-[#E65100]/80 font-bold block leading-tight">Doanh thu Dạt</span>
                                  <span className="font-extrabold text-xs text-[#D84315]">{formatVnd(stat.badRevenue)}</span>
                                </div>
                              </div>
                            </div>

                            {/* Visual progress bar representation indicating percentage of standard premium goods */}
                            <div className="pl-1.5 space-y-1">
                              <div className="flex items-center justify-between text-[10px] font-bold text-gray-400">
                                <span>Tỷ lệ hàng ngon đạt tiêu chuẩn:</span>
                                <span className="text-[#2E7D32] font-black">{goodPercentage}%</span>
                              </div>
                              <div className="w-full bg-gray-100 rounded-full h-1.5 overflow-hidden flex font-sans">
                                <div
                                  style={{ width: `${goodPercentage}%`, backgroundColor: itemAccent }}
                                  className="h-full rounded-full transition-all duration-300"
                                ></div>
                              </div>
                            </div>

                          </div>
                        );
                      })}
                    </div>
                  )}
                </motion.div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Floating Vietnamese Mobile Pill-styled navigation bar */}
      <div className="fixed bottom-0 inset-x-0 bg-gradient-to-t from-black/20 via-black/5 to-transparent pb-4 pt-10 pointer-events-none z-40 lg:hidden flex justify-center">
        <div className="bg-white flex items-center justify-between p-1.5 max-w-[420px] w-[90%] rounded-full border border-[#DDE5D6]/80 shadow-lg pointer-events-auto">
          {[
            { id: "input", label: "Nhập mới", icon: Sprout },
            { id: "history", label: "Lịch sử", icon: History },
            { id: "summary", label: "Tổng kết", icon: FileSpreadsheet }
          ].map((tab) => {
            const isSelected = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => {
                  setActiveTab(tab.id);
                  setShowProductManager(false);
                }}
                className={`flex-1 py-3 rounded-full font-black text-xs transition-all duration-200 flex items-center justify-center space-x-1 ${
                  isSelected
                    ? "bg-[#1B3E1C] text-white shadow-sm"
                    : "text-[#55604C] hover:bg-gray-50 hover:text-[#1B3E1C]"
                }`}
              >
                <tab.icon className="w-4 h-4 shrink-0" />
                <span>{tab.label}</span>
              </button>
            );
          })}
        </div>
      </div>

      {/* Pop-up Moduling Section: Confirmation warnings & Interactive alerts complying with 'đã hiểu' closing */}

      {/* Floating success toast that automatically slides down and disappears */}
      <AnimatePresence>
        {toast && (
          <div className="fixed top-8 inset-x-0 z-55 flex justify-center pointer-events-none px-4">
            <motion.div
              initial={{ opacity: 0, y: -20, scale: 0.9 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: -20, scale: 0.9 }}
              className="bg-[#1B3E1C] text-[#FFF4D6] border-2 border-[#D4A373] rounded-full py-2.5 px-6 shadow-lg flex items-center space-x-2.5 pointer-events-auto max-w-sm text-xs font-extrabold"
            >
              <Check className="w-4 h-4 shrink-0 text-[#EFFFD0]" />
              <span>{toast.message}</span>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* 1. Universal Notification Alerts Modal with 'Đã hiểu' dismissive option */}
      <AnimatePresence>
        {customAlert && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs">
            <motion.div
              initial={{ scale: 0.95, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.95, opacity: 0 }}
              className="bg-white rounded-[24px] max-w-sm w-full p-6 shadow-xl border border-gray-150 space-y-4 text-center"
            >
              <div className="flex justify-center">
                <div className={`w-14 h-14 rounded-full flex items-center justify-center ${customAlert.isError ? "bg-red-50 text-red-650" : "bg-emerald-50 text-emerald-600"}`}>
                  {customAlert.isError ? <AlertCircle className="w-7 h-7" /> : <Check className="w-7 h-7" />}
                </div>
              </div>
              <h3 className="text-base font-black text-gray-900">
                {customAlert.isError ? "Thông báo lỗi" : "Thành công"}
              </h3>
              <p className="text-xs text-gray-500 leading-relaxed">
                {customAlert.message}
              </p>
              <button
                onClick={() => setCustomAlert(null)}
                className={`w-full text-white h-11 rounded-xl text-xs font-black shadow-md transition ${customAlert.isError ? "bg-red-600 hover:bg-red-700 font-extrabold" : "bg-[#2E7D32] hover:bg-[#256428]"}`}
              >
                Đã hiểu
              </button>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* 2. Reset Season Warning dialog confirmation popup */}
      <AnimatePresence>
        {showResetConfirm && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs">
            <motion.div
              initial={{ scale: 0.95, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.95, opacity: 0 }}
              className="bg-white rounded-[24px] max-w-sm w-full p-6 shadow-xl border border-red-500/10 space-y-4 text-center"
            >
              <div className="flex justify-center">
                <div className="w-14 h-14 rounded-full bg-red-100 flex items-center justify-center text-red-650">
                  <RefreshCw className="w-7 h-7 text-red-600 animate-spin-slow" />
                </div>
              </div>
              <h3 className="text-base font-black text-gray-900">
                Xác nhận xoá toàn bộ dữ liệu?
              </h3>
              <p className="text-xs text-gray-500 leading-relaxed">
                Lưu ý: Hành động này sẽ xoá sạch hoàn toàn tất cả Lịch sử thu hoạch và khôi phục cơ sở dữ liệu cây trồng mặc định về thuở sơ khai ban đầu. Bạn có chắc chắn muốn làm mới vụ mùa hiện tại không?
              </p>
              <div className="flex space-x-2.5 pt-2">
                <button
                  onClick={() => setShowResetConfirm(false)}
                  className="flex-1 border border-gray-255 hover:bg-gray-50 text-gray-600 h-10 rounded-xl text-xs font-bold transition"
                >
                  Hủy bỏ
                </button>
                <button
                  onClick={handleResetSeason}
                  className="flex-1 bg-red-600 hover:bg-red-700 text-white h-10 rounded-xl text-xs font-black shadow-sm transition"
                >
                  Có, làm mới
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* 3. Delete Record Dialog Modal Warning */}
      <AnimatePresence>
        {pendingDeleteRecord && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs">
            <motion.div
              initial={{ scale: 0.95, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.95, opacity: 0 }}
              className="bg-white rounded-[24px] max-w-sm w-full p-6 shadow-xl border border-red-500/10 space-y-4 text-center"
            >
              <div className="flex justify-center">
                <div className="w-14 h-14 rounded-full bg-red-100 flex items-center justify-center text-red-650">
                  <Trash2 className="w-7 h-7 text-red-600 animate-pulse" />
                </div>
              </div>
              <h3 className="text-base font-black text-gray-900">
                Xóa dòng ghi chép này?
              </h3>
              <p className="text-xs text-gray-500 leading-relaxed">
                Bạn có chắc chắn muốn gỡ bỏ vĩnh viễn dòng thu hoạch ngày{" "}
                <span className="font-extrabold text-gray-800">
                  {formatDisplayDate(pendingDeleteRecord.dateTime)}
                </span>{" "}
                cơ bản của loại nông sản <span className="font-extrabold text-[#2E7D32]">{pendingDeleteRecord.product}</span> không?
              </p>
              <div className="flex space-x-2.5 pt-2">
                <button
                  onClick={() => setPendingDeleteRecord(null)}
                  className="flex-1 border border-gray-200 hover:bg-gray-50 text-gray-700 h-10 rounded-xl text-xs font-bold transition"
                >
                  Quay lại
                </button>
                <button
                  onClick={confirmDeleteRecord}
                  className="flex-1 bg-red-600 hover:bg-red-700 text-white h-10 rounded-xl text-xs font-extrabold shadow-sm transition"
                >
                  Xác nhận xóa
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* 4. Delete Product Category Dialog Modal Warning */}
      <AnimatePresence>
        {pendingDeleteProduct && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs">
            <motion.div
              initial={{ scale: 0.95, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.95, opacity: 0 }}
              className="bg-white rounded-[24px] max-w-sm w-full p-6 shadow-xl border border-red-500/10 space-y-4 text-center"
            >
              <div className="flex justify-center">
                <div className="w-14 h-14 rounded-full bg-red-100 flex items-center justify-center text-red-650">
                  <Trash2 className="w-7 h-7 text-red-600" />
                </div>
              </div>
              <h3 className="text-base font-black text-gray-900">
                Xóa loại nông sản?
              </h3>
              <p className="text-xs text-gray-500 leading-relaxed">
                Bạn đang tìm cách loại bỏ hoàn toàn danh mục nông sản gieo trồng{" "}
                <span className="font-extrabold text-[#1B5E20]">"{pendingDeleteProduct.name}"</span> ra khỏi danh sách lựa chọn nhanh. Hãy xác nhận?
              </p>
              <div className="flex space-x-2.5 pt-2">
                <button
                  onClick={() => setPendingDeleteProduct(null)}
                  className="flex-1 border border-gray-255 hover:bg-gray-50 text-gray-600 h-10 rounded-xl text-xs font-bold transition"
                >
                  Giữ lại
                </button>
                <button
                  onClick={confirmDeleteProduct}
                  className="flex-1 bg-red-600 hover:bg-red-700 text-white h-10 rounded-xl text-xs font-extrabold shadow-sm transition"
                >
                  Xóa bỏ
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
}
