FROM trinodb/trino:latest

# Optional: Add custom configurations
# COPY config.properties /etc/trino/config.properties
# COPY catalog/ /etc/trino/catalog/

EXPOSE 8080

CMD ["/usr/lib/trino/bin/run-trino"]
