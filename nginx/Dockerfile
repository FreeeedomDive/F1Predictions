FROM nginx:alpine
COPY nginx.prod.conf /etc/nginx/nginx.conf

ENTRYPOINT ["nginx", "-g", "daemon off;"]
