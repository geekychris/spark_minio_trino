#!/bin/bash
# Clean up all lakehouse resources from Kubernetes

echo "Cleaning up lakehouse resources..."
echo ""

kubectl delete -f service.yaml
kubectl delete -f deployment.yaml
kubectl delete -f configmap.yaml

echo ""
echo "✓ All resources removed!"
echo ""
