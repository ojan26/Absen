import express from "express";
import path from "path";
import { createServer as createViteServer } from "vite";
import { GoogleGenAI, Type } from "@google/genai";
import dotenv from "dotenv";

dotenv.config();

// Initialize Gemini SDK securely on server-side with robust quote-stripping
const getCleanApiKey = () => {
  let key = process.env.GEMINI_API_KEY || "";
  key = key.trim();
  if (key.startsWith('"') && key.endsWith('"')) {
    key = key.substring(1, key.length - 1);
  }
  if (key.startsWith("'") && key.endsWith("'")) {
    key = key.substring(1, key.length - 1);
  }
  return key.trim();
};

const ai = new GoogleGenAI({
  apiKey: getCleanApiKey(),
  httpOptions: {
    headers: {
      'User-Agent': 'aistudio-build',
    }
  }
});

async function startServer() {
  const app = express();
  const PORT = 3000;

  app.use(express.json());

  // API Routes
  app.post("/api/ai/holidays", async (req, res) => {
    try {
      const { month, year } = req.body;
      if (!month || !year) {
        return res.status(400).json({ error: "Bulan (month) dan tahun (year) wajib diisi." });
      }

      const monthNamesIndo = [
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
      ];
      const monthName = monthNamesIndo[month - 1] || String(month);

      const prompt = `Berikan daftar semua Hari Libur Nasional resmi di Indonesia pada bulan ${monthName} tahun ${year}. Pastikan tanggal akurat untuk kalender libur Indonesia ${year}. Jika tidak ada hari libur nasional pada bulan tersebut, kembalikan array kosong.`;

      const response = await ai.models.generateContent({
        model: "gemini-3.5-flash",
        contents: prompt,
        config: {
          systemInstruction: "Anda adalah asisten administrasi sekolah di Indonesia yang sangat ahli dan akurat tentang penanggalan libur nasional resmi Republik Indonesia.",
          responseMimeType: "application/json",
          responseSchema: {
            type: Type.ARRAY,
            items: {
              type: Type.OBJECT,
              properties: {
                tanggal: {
                  type: Type.STRING,
                  description: "Tanggal hari libur dalam format YYYY-MM-DD (misal: '2026-06-01')",
                },
                nama: {
                  type: Type.STRING,
                  description: "Nama resmi hari libur nasional dalam bahasa Indonesia (misal: 'Hari Lahir Pancasila')",
                },
                keterangan: {
                  type: Type.STRING,
                  description: "Keterangan singkat tentang hari libur tersebut dalam bahasa Indonesia",
                }
              },
              required: ["tanggal", "nama", "keterangan"]
            }
          }
        }
      });

      const text = response.text || "[]";
      const holidays = JSON.parse(text.trim());
      res.json({ holidays });
    } catch (error: any) {
      console.error("Error generating holidays with Gemini:", error);
      res.status(500).json({ error: error.message || "Gagal mendapatkan data hari libur dari AI" });
    }
  });

  // Proxy for Google Sheets Apps Script API (to avoid browser CORS and redirects issues)
  app.get("/api/binding-device/proxy", async (req, res) => {
    try {
      const { scriptUrl } = req.query;
      if (!scriptUrl || typeof scriptUrl !== "string") {
        return res.status(400).json({ error: "Parameter scriptUrl wajib disertakan." });
      }

      const response = await fetch(scriptUrl, {
        method: "GET",
        headers: {
          "Accept": "application/json"
        }
      });

      if (!response.ok) {
        return res.status(response.status).json({ error: `Gagal menghubungi Google Apps Script (HTTP ${response.status})` });
      }

      const data = await response.json();
      res.json(data);
    } catch (error: any) {
      console.error("Error in GET /api/binding-device/proxy:", error);
      res.status(500).json({ error: error.message || "Gagal memproses permintaan proxy." });
    }
  });

  app.post("/api/binding-device/proxy", async (req, res) => {
    try {
      const { scriptUrl, payload } = req.body;
      if (!scriptUrl || typeof scriptUrl !== "string") {
        return res.status(400).json({ error: "Parameter scriptUrl wajib disertakan." });
      }
      if (!payload) {
        return res.status(400).json({ error: "Body payload wajib disertakan." });
      }

      const response = await fetch(scriptUrl, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Accept": "application/json"
        },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        return res.status(response.status).json({ error: `Gagal memproses data di Google Apps Script (HTTP ${response.status})` });
      }

      const data = await response.json();
      res.json(data);
    } catch (error: any) {
      console.error("Error in POST /api/binding-device/proxy:", error);
      res.status(500).json({ error: error.message || "Gagal memproses permintaan proxy." });
    }
  });

  // Vite middleware for development
  if (process.env.NODE_ENV !== "production") {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa",
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`Server running on http://0.0.0.0:${PORT}`);
  });
}

startServer();
