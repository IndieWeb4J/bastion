FROM gcr.io/distroless/java25-debian13

WORKDIR /app
COPY bastion.jar /app/bastion.jar

EXPOSE 8080
CMD ["/app/bastion.jar"]
