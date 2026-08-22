#!/bin/bash
set -e

echo "=== INITIALISIERE LIFE-OS MAC CORE NODE ==="

STORAGE_DIR="$HOME/LifeOS_StorageStacks"
SERVER_DIR="/usr/local/lifeos"
LOG_DIR="/var/log/lifeos"

sudo mkdir -p "$SERVER_DIR" "$LOG_DIR"
mkdir -p "$STORAGE_DIR"
sudo chown -R $(whoami) "$SERVER_DIR" "$LOG_DIR" "$STORAGE_DIR"

echo "⚡ Konfiguriere DarkWake Power Management..."
sudo pmset -a womp 1 powernap 1 tcpkeepalive 1 ring 1
sudo pmset -a disablesleep 0

pip3 install websockets playwright

cp mac_rendezvous_server.py mac_bootengine_extractor.py deepsearch_worker.py "$SERVER_DIR/"
sudo cp com.lifeos.core.plist /Library/LaunchDaemons/
sudo chown root:wheel /Library/LaunchDaemons/com.lifeos.core.plist
sudo launchctl load -w /Library/LaunchDaemons/com.lifeos.core.plist

echo "✅ Mac Core Node eingerichtet."
