.PHONY: help build up down restart logs clean ps health backup restore

help: ## Show this help message
	@echo 'Usage: make [target]'
	@echo ''
	@echo 'Available targets:'
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  %-15s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

build: ## Build all Docker images
	docker-compose build

up: ## Start all services
	docker-compose up -d

down: ## Stop all services
	docker-compose down

restart: ## Restart all services
	docker-compose restart

logs: ## Show logs for all services
	docker-compose logs -f

logs-backend: ## Show backend logs
	docker-compose logs -f backend

logs-frontend: ## Show frontend logs
	docker-compose logs -f frontend

logs-postgres: ## Show PostgreSQL logs
	docker-compose logs -f postgres

logs-keycloak: ## Show Keycloak logs
	docker-compose logs -f keycloak

ps: ## Show running containers
	docker-compose ps

health: ## Check health of all services
	@echo "Checking service health..."
	@curl -f http://localhost:8080/actuator/health || echo "Backend: UNHEALTHY"
	@curl -f http://localhost:80 || echo "Frontend: UNHEALTHY"
	@curl -f http://localhost:8090/health/ready || echo "Keycloak: UNHEALTHY"

clean: ## Remove all containers, volumes, and images
	docker-compose down -v
	docker system prune -af

backup: ## Backup PostgreSQL database
	docker-compose exec postgres pg_dump -U finans_user finans_db > backup_$(shell date +%Y%m%d_%H%M%S).sql
	@echo "Backup created: backup_$(shell date +%Y%m%d_%H%M%S).sql"

restore: ## Restore PostgreSQL database (usage: make restore FILE=backup.sql)
	@if [ -z "$(FILE)" ]; then echo "Usage: make restore FILE=backup.sql"; exit 1; fi
	docker-compose exec -T postgres psql -U finans_user finans_db < $(FILE)
	@echo "Database restored from $(FILE)"

rebuild: ## Rebuild and restart all services
	docker-compose down
	docker-compose build --no-cache
	docker-compose up -d

rebuild-backend: ## Rebuild and restart backend only
	docker-compose stop backend
	docker-compose build --no-cache backend
	docker-compose up -d backend

rebuild-frontend: ## Rebuild and restart frontend (no cache — slow, full re-install)
	docker-compose stop frontend
	docker-compose build --no-cache frontend
	docker-compose up -d frontend

front: ## Fast frontend rebuild for code changes (uses npm cache — ~20-40s)
	docker-compose build frontend
	docker-compose up -d frontend
	@echo ""
	@echo "✓ Frontend rebuilt. Hard refresh the browser (Ctrl+Shift+R) to bust the asset cache."

dev: ## Dev mode — frontend with HMR (code changes appear instantly, no rebuilds)
	@echo "Starting dev stack — frontend served by Vite with HMR on http://localhost"
	@echo "(backend, postgres, keycloak all keep their prod images)"
	docker-compose -f docker-compose.yml -f docker-compose.dev.yml up -d
	@echo ""
	@echo "✓ Dev stack up. Frontend logs:"
	@echo "    make logs-frontend"
	@echo ""
	@echo "  When you're done, switch back to prod with:"
	@echo "    make down && make up"

dev-down: ## Stop the dev stack
	docker-compose -f docker-compose.yml -f docker-compose.dev.yml down

shell-backend: ## Open shell in backend container
	docker-compose exec backend bash

shell-postgres: ## Open PostgreSQL shell
	docker-compose exec postgres psql -U finans_user -d finans_db

stats: ## Show container resource usage
	docker stats

prod-up: ## Start production environment
	docker-compose -f docker-compose.prod.yml up -d

prod-down: ## Stop production environment
	docker-compose -f docker-compose.prod.yml down

prod-logs: ## Show production logs
	docker-compose -f docker-compose.prod.yml logs -f
