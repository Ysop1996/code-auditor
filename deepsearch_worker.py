import asyncio
import hashlib
from playwright.async_api import async_playwright

class DeepSearchWorker:
    def __init__(self):
        self.browser = None

    async def init_engine(self):
        playwright = await async_playwright().start()
        self.browser = await playwright.chromium.launch(headless=True)

    async def search_primitive(self, target_primitive: str) -> list[dict]:
        page = await self.browser.new_page()
        query = target_primitive.replace(" ", "+")
        search_url = f"https://html.duckduckgo.com/html/?q={query}"
        
        await page.goto(search_url)
        links = await page.eval_on_selector_all(".result__url", "elements => elements.map(e => e.href)")
        
        results = []
        for url in links[:2]:
            try:
                sub_page = await self.browser.new_page()
                await sub_page.goto(url, timeout=5000)
                
                raw_text = await sub_page.evaluate("""() => {
                    const remove = ['script', 'style', 'nav', 'footer', 'iframe'];
                    remove.forEach(tag => document.querySelectorAll(tag).forEach(el => el.remove()));
                    return document.body.innerText;
                }""")
                
                cleaned = "\n".join([line.strip() for line in raw_text.splitlines() if len(line.strip()) > 30][:10])
                results.append({
                    "primitive": target_primitive,
                    "sha256": hashlib.sha256(cleaned.encode()).hexdigest(),
                    "payload": cleaned
                })
                await sub_page.close()
            except Exception:
                continue

        await page.close()
        return results
