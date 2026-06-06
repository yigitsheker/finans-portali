-- ShedLock distributed-lock table. Each @SchedulerLock job acquires a row here
-- so that, when multiple backend instances run (GKE HPA), only ONE instance
-- executes a given scheduled job per its window. Standard ShedLock schema.
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
