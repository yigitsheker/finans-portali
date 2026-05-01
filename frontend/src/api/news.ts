import { createApi } from "./http";
import type Keycloak from "keycloak-js";

export type NewsItem = {
    id: number;
    title: string;
    summary: string;
    category: string;
    publishedAt: string;
};

export async function fetchNews(keycloak: Keycloak) {
    const api = createApi(keycloak);
    const res = await api.get<NewsItem[]>("/api/v1/news");
    return res.data;
}