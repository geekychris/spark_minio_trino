#!/bin/bash
# Lakehouse Demo Stop Script
# This script stops all services and optionally cleans up volumes

echo "=========================================="
echo "Stopping Lakehouse Demo Environment"
echo "=========================================="
echo ""

# Check if user wants to remove volumes
if [ "$1" == "--clean" ]; then
    echo "Stopping all services and removing volumes..."
    docker compose down -v
    echo ""
    echo "✓ All services stopped and data volumes removed"
else
    echo "Stopping all services (keeping data volumes)..."
    docker compose down
    echo ""
    echo "✓ All services stopped"
    echo ""
    echo "Note: Data volumes are preserved. To remove them, run:"
    echo "  ./stop_lakehouse.sh --clean"
fi

echo ""
