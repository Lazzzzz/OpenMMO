FROM python:3.13-alpine

RUN apk add --no-cache docker-cli

WORKDIR /app
COPY server.py /app/server.py

USER root
EXPOSE 8081
CMD ["python", "/app/server.py"]
