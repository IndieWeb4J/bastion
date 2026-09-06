FROM gcr.io/distroless/base-debian12

WORKDIR /app
COPY bastion /app/bastion

EXPOSE 8080
ENTRYPOINT ["/app/bastion"]
