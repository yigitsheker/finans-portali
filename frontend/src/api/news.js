import { createApi } from "./http";

export async function fetchNews(keycloak) {
    const api = createApi(keycloak);
    const res = await api.get("/api/v1/news");
    return res.data;
}
