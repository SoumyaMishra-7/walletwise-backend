# API

`POST /auth/register` accepts `name`, `email`, and an eight-character minimum `password`. `POST /auth/login` returns a bearer token. Protected CRUD: `POST`, `GET`, `GET /{id}`, `PUT /{id}`, and `DELETE /{id}` on `/transactions`; list supports `page`, `size`, and `sort`. Transaction input is `type`, `category`, `amount`, `description`, and optional `date`.
