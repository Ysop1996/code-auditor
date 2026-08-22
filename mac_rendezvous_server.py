import asyncio
import hashlib
import hmac
import json
import os
import secrets

PORT = 8443
STORAGE_PATH = os.path.expanduser("~/LifeOS_StorageStacks")
PAIRING_SECRET_FILE = "/usr/local/lifeos/pairing_token.key"

def load_or_create_secret() -> str:
    if not os.path.exists(PAIRING_SECRET_FILE):
        token = secrets.token_hex(32)
        with open(PAIRING_SECRET_FILE, "w") as f:
            f.write(token)
        os.chmod(PAIRING_SECRET_FILE, 0o600)
        return token
    with open(PAIRING_SECRET_FILE, "r") as f:
        return f.read().strip()

SHARED_SECRET = load_or_create_secret()

async def handle_edge_node(reader: asyncio.StreamReader, writer: asyncio.StreamWriter):
    peer = writer.get_extra_info('peername')
    try:
        client_auth = (await reader.readexactly(64)).decode()
        expected_auth = hashlib.sha256(SHARED_SECRET.encode()).hexdigest()
        
        if not hmac.compare_digest(client_auth, expected_auth):
            writer.close()
            await writer.wait_closed()
            return

        writer.write(b"AUTH_OK\n")
        await writer.drain()

        command = (await reader.readexactly(4)).decode()

        if command == "PING":
            writer.write(b"PONG_MAC_READY\n")
            await writer.drain()

        elif command == "SYNC":
            payload_len = int.from_bytes(await reader.readexactly(4), "big")
            raw_payload = await reader.readexactly(payload_len)
            data = json.loads(raw_payload.decode())

            target_file = os.path.join(STORAGE_PATH, f"{data['id']}.json")
            with open(target_file, "w") as f:
                json.dump(data, f, indent=2)

            response = b"ACK_SAVED"
            writer.write(len(response).to_bytes(4, "big") + response)
            await writer.drain()

        elif command == "BOOT":
            summary_path = os.path.join(STORAGE_PATH, "_genesis_summary.json")
            if os.path.exists(summary_path):
                with open(summary_path, "rb") as f:
                    content = f.read()
                writer.write(len(content).to_bytes(4, "big") + content)
            else:
                writer.write((0).to_bytes(4, "big"))
            await writer.drain()

    except Exception:
        pass
    finally:
        writer.close()
        await writer.wait_closed()

async def main():
    os.makedirs(STORAGE_PATH, exist_ok=True)
    server = await asyncio.start_server(handle_edge_node, "0.0.0.0", PORT)
    async with server:
        await server.serve_forever()

if __name__ == "__main__":
    asyncio.run(main())
