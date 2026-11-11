# University Portal — front end

Angular single-page application backed by the Java API: sign in, schedule, course registration,
grades, transcript, attendance, library and messages. Navigation is filtered by role.

## Development server

```bash
npm install
npm start
```

Open `http://localhost:4200`. Requests to `/api` are proxied to the backend on `http://localhost:8080`
through `proxy.conf.json`, so start the backend first:

```bash
cd ../backend && ./build.sh && ./run-server.sh
```

## Build

```bash
npm run build     # output in dist/
```

The build emits static files; serve them behind any web server that forwards `/api` to the Java
backend.

## Structure

```
src/app
  core/       auth, schedule and portal services, api.ts, page.ts, auth.guard.ts
  login/      sign-in page
  schedule/   timetable and enrolled courses
  register/   course registration
  grades/     marks, or mark entry for teachers
  transcript/ credits and GPA
  attendance/ summary, or marking for teachers
  library/    books and loans
  messages/   inbox, sent and compose
  topbar/     navigation, signed-in user and sign out
```

Styling is intentionally minimal: everything lives in `src/styles.css`.
