import { createApi } from "./http";

export function createAdminApi(keycloak) {
    const api = createApi(keycloak);

    return {
        listUsers: (search = "", first = 0, max = 100) =>
            api.get("/api/v1/admin/users", { params: { search, first, max } })
               .then((r) => r.data),

        banUser: (id) =>
            api.post(`/api/v1/admin/users/${id}/ban`).then((r) => r.data),

        unbanUser: (id) =>
            api.post(`/api/v1/admin/users/${id}/unban`).then((r) => r.data),

        require2fa: (id) =>
            api.post(`/api/v1/admin/users/${id}/require-2fa`).then((r) => r.data),

        reset2fa: (id) =>
            api.post(`/api/v1/admin/users/${id}/reset-2fa`).then((r) => r.data),
    };
}
