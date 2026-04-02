/**
 * Cloudflare Worker for BulkSender AI Agent.
 * Accepts chat requests from the Android app and proxies them to Gemini.
 */

const ALLOWED_ACTIONS = new Set(["ADD_CONTACT", "SHOW_CHIPS", "SHOW_GROUPS", "NONE"]);
const CORS_HEADERS = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type"
};

function jsonResponse(body, status = 200) {
    return new Response(JSON.stringify(body), {
        status,
        headers: {
            ...CORS_HEADERS,
            "Content-Type": "application/json"
        }
    });
}

function sanitizeHistory(history) {
    if (!Array.isArray(history)) {
        return [];
    }

    return history
        .slice(-10)
        .map((item) => {
            const role = item?.role === "model" ? "model" : "user";
            const text = Array.isArray(item?.parts)
                ? item.parts
                    .map((part) => (typeof part?.text === "string" ? part.text.trim() : ""))
                    .filter(Boolean)
                    .join("\n")
                : "";

            if (!text) {
                return null;
            }

            return {
                role,
                parts: [{ text }]
            };
        })
        .filter(Boolean);
}

function extractJsonObject(text) {
    const match = text.match(/\{[\s\S]*\}/);
    if (!match) {
        throw new Error("No JSON object found in Gemini response.");
    }

    return JSON.parse(match[0]);
}

function normalizeAction(action) {
    return ALLOWED_ACTIONS.has(action) ? action : "NONE";
}

export default {
    async fetch(request, env) {
        const url = new URL(request.url);

        if (request.method === "OPTIONS") {
            return new Response(null, { status: 204, headers: CORS_HEADERS });
        }

        if (request.method === "GET" && (url.pathname === "/" || url.pathname === "/health")) {
            return jsonResponse({
                ok: true,
                service: "bulksender-ai-agent",
                endpoint: "/chat"
            });
        }

        if (request.method !== "POST") {
            return jsonResponse({ error: "Method Not Allowed" }, 405);
        }

        if (url.pathname !== "/" && url.pathname !== "/chat") {
            return jsonResponse({ error: "Not Found" }, 404);
        }

        if (!env.GEMINI_API_KEY) {
            return jsonResponse({ error: "GEMINI_API_KEY is not configured." }, 500);
        }

        try {
            const body = await request.json();
            const message = typeof body?.message === "string" ? body.message.trim() : "";
            const history = sanitizeHistory(body?.history);
            const plan = body?.plan === "premium" ? "premium" : "free";

            if (!message) {
                return jsonResponse({ error: "Message is required." }, 400);
            }

            const systemPrompt = [
                "You are the AI assistant for the BulkSend WhatsApp marketing app.",
                "Always reply in simple Hinglish.",
                "Guide the user step-by-step to create or continue a WhatsApp bulk messaging campaign.",
                "If contacts are not added yet, ask the user to use the Add Contact Now button first.",
                "After contacts are added, ask whether they want Proceed with Old Contacts or Create New Group.",
                `Current user plan: ${plan}.`,
                "If the plan is free, remind them they can send up to 10 messages.",
                "If the plan is premium, remind them they have unlimited sending.",
                "Do not give technical details unless the user asks.",
                "Return valid JSON only with keys reply, action, and context.",
                "Allowed action values are ADD_CONTACT, SHOW_CHIPS, SHOW_GROUPS, and NONE."
            ].join("\n");

            const geminiPayload = {
                contents: [
                    { role: "user", parts: [{ text: systemPrompt }] },
                    ...history,
                    { role: "user", parts: [{ text: message }] }
                ],
                generationConfig: {
                    temperature: 0.7,
                    responseMimeType: "application/json"
                }
            };

            const model = "gemini-2.0-flash";
            const response = await fetch(
                `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${env.GEMINI_API_KEY}`,
                {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(geminiPayload)
                }
            );

            if (!response.ok) {
                const errorText = await response.text();
                return jsonResponse(
                    {
                        error: "Gemini API request failed.",
                        details: errorText
                    },
                    502
                );
            }

            const data = await response.json();
            const aiText = data?.candidates?.[0]?.content?.parts
                ?.map((part) => (typeof part?.text === "string" ? part.text : ""))
                .join("")
                .trim();

            if (!aiText) {
                return jsonResponse({ error: "Gemini returned an empty response." }, 502);
            }

            let result;
            try {
                result = JSON.parse(aiText);
            } catch {
                try {
                    result = extractJsonObject(aiText);
                } catch {
                    result = {
                        reply: aiText,
                        action: "NONE",
                        context: null
                    };
                }
            }

            return jsonResponse({
                reply: typeof result?.reply === "string" && result.reply.trim()
                    ? result.reply.trim()
                    : aiText,
                action: normalizeAction(result?.action),
                context: typeof result?.context === "string" ? result.context : null
            });
        } catch (error) {
            return jsonResponse(
                {
                    error: error instanceof Error ? error.message : "Unknown worker error."
                },
                500
            );
        }
    }
};
