package com.interviewiq.ai;

import com.interviewiq.domain.Difficulty;
import com.interviewiq.domain.Question;
import com.interviewiq.domain.QuestionSource;
import java.util.ArrayList;
import java.util.List;

/**
 * The curated question bank loaded into the database on first boot.
 *
 * Each entry carries:
 *   - the concepts a strong answer should mention (the grading rubric),
 *   - a model answer,
 *   - skill tags, which is what lets the generator pick questions that match a
 *     student's declared skills rather than just their target role.
 */
public final class QuestionBank {

    private QuestionBank() {
    }

    private record Row(String role, String topic, Difficulty difficulty, String text,
                       String keywords, String ideal, String skills) {
    }

    public static List<Question> seed() {
        List<Question> out = new ArrayList<>();
        for (Row r : ROWS) {
            out.add(new Question(r.text(), r.role(), r.topic(), r.difficulty(),
                    r.keywords(), r.ideal(), QuestionSource.BANK, r.skills()));
        }
        return out;
    }

    /** All skill tags used by the bank — surfaced so the UI can suggest skills. */
    public static List<String> allSkillTags() {
        return ROWS.stream()
                .flatMap(r -> List.of(r.skills().split(",")).stream())
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted()
                .toList();
    }

    private static final List<Row> ROWS = List.of(
            // ── Frontend ─────────────────────────────────────────────────────
            new Row("Frontend Engineer", "JavaScript", Difficulty.EASY,
                    "What's the difference between let, const, and var in JavaScript?",
                    "hoisting,block scope,reassignment,temporal dead zone,function scope",
                    "var is function-scoped and hoisted; let/const are block-scoped and live in a temporal dead zone. const prevents rebinding, not mutation.",
                    "JavaScript,ES6"),
            new Row("Frontend Engineer", "React", Difficulty.EASY,
                    "What problem do React hooks solve compared to class components?",
                    "state,lifecycle,reuse,functional components,custom hooks",
                    "Hooks give function components state and effects, and let stateful logic be extracted into custom hooks instead of HOCs/render props.",
                    "React,JavaScript"),
            new Row("Frontend Engineer", "CSS", Difficulty.MEDIUM,
                    "Explain the CSS box model and how box-sizing changes layout.",
                    "margin,border,padding,content,border-box,width",
                    "Content, padding, border, margin from inside out. border-box makes width include padding+border; content-box does not.",
                    "CSS,HTML"),
            new Row("Frontend Engineer", "Performance", Difficulty.HARD,
                    "How would you diagnose and fix a React app that janks while scrolling a long list?",
                    "virtualization,re-render,memo,profiler,windowing,keys",
                    "Profile with React DevTools, cut re-renders (memo, stable keys/refs), and virtualise the list so only visible rows render.",
                    "React,Performance"),
            new Row("Frontend Engineer", "Browser", Difficulty.MEDIUM,
                    "Walk me through what happens from typing a URL to seeing pixels.",
                    "dns,tcp,tls,http,render,critical path",
                    "DNS → TCP → TLS → HTTP request/response → DOM/CSSOM → layout → paint → composite; critical path blocks rendering.",
                    "HTML,JavaScript,Networking"),
            new Row("Frontend Engineer", "Testing", Difficulty.MEDIUM,
                    "How do you decide what to cover with unit tests versus end-to-end tests?",
                    "pyramid,critical paths,integration,mocking,cost",
                    "Many fast unit tests for logic, fewer integration tests for contracts, a handful of E2E tests for revenue-critical journeys.",
                    "Testing,JavaScript"),

            // ── Backend ──────────────────────────────────────────────────────
            new Row("Backend Engineer", "Java", Difficulty.MEDIUM,
                    "Explain how HashMap works internally and when lookups degrade.",
                    "hashing,buckets,collision,linked list,treeify,load factor",
                    "Array of buckets keyed by hashCode; collisions chain in a list that treeifies; resize at load factor 0.75; bad hashCode degrades to O(n).",
                    "Java,Collections"),
            new Row("Backend Engineer", "Spring Boot", Difficulty.MEDIUM,
                    "How does Spring Boot auto-configuration decide which beans to create?",
                    "conditional,auto-configuration,classpath,starter,ConditionalOnMissingBean",
                    "Starter jars register auto-configuration classes guarded by @ConditionalOnClass/@ConditionalOnMissingBean; your own beans override the defaults.",
                    "Spring Boot,Java"),
            new Row("Backend Engineer", "API Design", Difficulty.HARD,
                    "Design rate limiting for a public REST API.",
                    "token bucket,sliding window,redis,429,distributed",
                    "Token bucket per key in Redis with atomic Lua; sliding window for bursts; return 429 with Retry-After; keep the limit check outside the request hot path where possible.",
                    "API Design,System Design"),
            new Row("Backend Engineer", "Databases", Difficulty.MEDIUM,
                    "When would you add an index, and what does it cost?",
                    "b-tree,selectivity,write amplification,explain,covering index",
                    "Index selective filter/join columns; cost is write amplification, storage and maintenance; verify with EXPLAIN; consider covering indexes for hot reads.",
                    "SQL,Databases"),
            new Row("Backend Engineer", "Concurrency", Difficulty.HARD,
                    "Explain optimistic versus pessimistic locking with an example.",
                    "version column,row lock,conflict,retry,throughput",
                    "Optimistic uses a version column and retries on conflict — great read-heavy throughput; pessimistic takes row locks up front — simpler under heavy contention but blocks.",
                    "Java,Concurrency"),
            new Row("Backend Engineer", "Caching", Difficulty.MEDIUM,
                    "Describe cache invalidation strategies you've used.",
                    "ttl,write-through,eviction,stale,redis",
                    "TTL for bounded staleness, write-through or delete-on-write for correctness, LRU eviction, and awareness of stale negative caches.",
                    "Caching,Redis"),

            // ── Full stack ───────────────────────────────────────────────────
            new Row("Full Stack Engineer", "System Design", Difficulty.HARD,
                    "Design the data model and flows for a URL shortener at 10k writes/sec.",
                    "base62,sharding,hot key,cache,analytics,idempotency",
                    "Generate keys (counter/Base62 or preallocated ranges), shard by key prefix, cache hot redirects, separate write path from analytics, make writes idempotent.",
                    "System Design,Architecture"),
            new Row("Full Stack Engineer", "Auth", Difficulty.MEDIUM,
                    "Walk through the OAuth2 authorization code flow with PKCE.",
                    "pkce,code challenge,redirect,access token,refresh token",
                    "Client sends a code challenge, user authorises, server returns a code, client exchanges code + verifier for tokens; PKCE protects public clients.",
                    "Security,API Design"),
            new Row("Full Stack Engineer", "SQL", Difficulty.MEDIUM,
                    "How do you find and fix an N+1 query problem?",
                    "eager fetch,join,batch,orm,query log",
                    "Spot it in the query log or APM, then fetch in joins or batches — e.g. JPA fetch joins or entity graphs; watch for cartesian blowups.",
                    "SQL,Databases"),
            new Row("Full Stack Engineer", "Architecture", Difficulty.HARD,
                    "How would you introduce a message queue into a monolith?",
                    "decoupling,idempotency,outbox,backpressure,dead letter",
                    "Start with one async workflow, publish via an outbox table for exactly-once-ish delivery, make consumers idempotent, add dead-letter handling and backpressure.",
                    "Architecture,Messaging"),
            new Row("Full Stack Engineer", "Security", Difficulty.HARD,
                    "What defences does a public form need against abuse?",
                    "rate limit,captcha,validation,csrf,waf",
                    "Rate limiting, bot mitigation, server-side validation, CSRF protection, size limits and monitoring; treat every field as hostile.",
                    "Security,Web"),
            new Row("Full Stack Engineer", "Delivery", Difficulty.MEDIUM,
                    "How do you ship a feature behind a flag safely?",
                    "feature flag,canary,rollback,metrics,gradual rollout",
                    "Dark-launch behind a flag, canary to a small cohort, watch error/latency metrics, ramp gradually, keep a one-step rollback and clean the flag up afterwards.",
                    "CI/CD,DevOps"),

            // ── Data engineering ─────────────────────────────────────────────
            new Row("Data Engineer", "SQL", Difficulty.MEDIUM,
                    "Explain window functions and give a use case.",
                    "over,partition by,rank,running total,frame",
                    "They compute per-row values over a partition without collapsing rows — e.g. ROW_NUMBER for dedupe, SUM OVER for running totals.",
                    "SQL,Analytics"),
            new Row("Data Engineer", "Pipelines", Difficulty.HARD,
                    "How do you make a daily ETL job idempotent and replayable?",
                    "partition overwrite,watermark,dedupe,checkpoint,backfill",
                    "Write to date partitions and overwrite on rerun, dedupe on business keys, checkpoint streaming state, so any day can be backfilled safely.",
                    "ETL,SQL"),
            new Row("Data Engineer", "Kafka", Difficulty.MEDIUM,
                    "What are partitions and consumer groups for in Kafka?",
                    "partition,consumer group,offset,parallelism,ordering",
                    "Partitions give parallelism and per-partition ordering; consumer groups split partitions across consumers and track offsets.",
                    "Kafka,Streaming"),
            new Row("Data Engineer", "Modelling", Difficulty.MEDIUM,
                    "Star schema versus normalized schema for analytics — what do you choose?",
                    "fact,dimension,denormalized,joins,query performance",
                    "Star schemas denormalise dimensions for simpler, faster analytical queries; normalised models protect write integrity in OLTP.",
                    "Data Modeling,SQL"),
            new Row("Data Engineer", "Spark", Difficulty.HARD,
                    "What causes skew in Spark jobs and how do you fix it?",
                    "salting,broadcast join,partition,shuffle,aqe",
                    "Hot keys overload one partition; broadcast the small side, salt hot keys, enable AQE partition coalescing, verify in the Spark UI.",
                    "Spark,Big Data"),
            new Row("Data Engineer", "Storage", Difficulty.MEDIUM,
                    "Parquet versus CSV for analytics — why the fuss?",
                    "columnar,compression,predicate pushdown,schema,io",
                    "Parquet is columnar: far less I/O for wide scans, great compression, schema-typed, and supports predicate pushdown.",
                    "Big Data,Analytics"),

            // ── DevOps ───────────────────────────────────────────────────────
            new Row("DevOps Engineer", "Containers", Difficulty.MEDIUM,
                    "What actually happens when a container starts?",
                    "namespaces,cgroups,image layers,runtime,entrypoint",
                    "The runtime unpacks image layers, creates namespaces for isolation and cgroups for limits, then execs the entrypoint as PID 1.",
                    "Docker,Containers"),
            new Row("DevOps Engineer", "Kubernetes", Difficulty.HARD,
                    "How does a request reach a pod in Kubernetes?",
                    "service,kube-proxy,endpoints,ingress,dns",
                    "Ingress → Service DNS → kube-proxy/iptables routes to a ready endpoint pod; readiness gates and probes decide membership.",
                    "Kubernetes,Networking"),
            new Row("DevOps Engineer", "CI/CD", Difficulty.MEDIUM,
                    "Design a pipeline from commit to production.",
                    "build,test,artifact,staging,rollback,gates",
                    "Build once into an immutable artifact, run tests, deploy to staging, gate on health/metrics, promote the same artifact, keep rollback one step away.",
                    "CI/CD,DevOps"),
            new Row("DevOps Engineer", "Observability", Difficulty.MEDIUM,
                    "Which signals matter when debugging a latency spike?",
                    "p99,tracing,metrics,logs,saturation,RED",
                    "Check p95/p99 rather than averages, use traces to find the slow hop, then saturation metrics and logs to explain why.",
                    "Observability,Monitoring"),
            new Row("DevOps Engineer", "IaC", Difficulty.MEDIUM,
                    "Why is infrastructure as code worth the effort?",
                    "reproducibility,review,drift,automation,audit",
                    "Versioned, reviewable, reproducible infrastructure; drift is detectable and rebuilds are automated rather than tribal knowledge.",
                    "Terraform,DevOps"),
            new Row("DevOps Engineer", "Reliability", Difficulty.HARD,
                    "How do you set an SLO and what happens when it's burned?",
                    "sli,budget,alert,error budget,freeze",
                    "Define SLIs users feel, set a target SLO, alert on burn rate, and when the error budget is spent freeze risky changes and prioritise reliability.",
                    "SRE,Observability"),

            // ── ML engineering ───────────────────────────────────────────────
            new Row("ML Engineer", "Fundamentals", Difficulty.MEDIUM,
                    "Explain overfitting and three ways to counter it.",
                    "regularization,cross validation,more data,early stopping,dropout",
                    "The model memorises training noise; counter with regularisation, more/augmented data, and validation-based early stopping.",
                    "Machine Learning,Python"),
            new Row("ML Engineer", "Evaluation", Difficulty.HARD,
                    "How do you evaluate a ranking model offline and online?",
                    "ndcg,a/b test,offline online gap,holdout,clicks",
                    "Offline NDCG on a time-based holdout, then online A/B on engagement metrics, and expect a gap because offline data hides position bias.",
                    "Machine Learning,Statistics"),
            new Row("ML Engineer", "Production", Difficulty.HARD,
                    "How do you monitor a model in production?",
                    "drift,feature skew,shadow,fallback,metrics",
                    "Track input drift and feature skew, prediction distributions and business metrics; shadow new models and always have a heuristic fallback.",
                    "MLOps,Monitoring"),
            new Row("ML Engineer", "NLP", Difficulty.MEDIUM,
                    "When is fine-tuning better than prompt engineering?",
                    "data volume,cost,latency,domain shift,maintenance",
                    "Fine-tune when you have volume, a stable narrow task and latency/cost pressure; prompts win for flexibility and fast iteration.",
                    "NLP,Machine Learning"),
            new Row("ML Engineer", "Data", Difficulty.MEDIUM,
                    "How do you handle class imbalance?",
                    "resampling,class weights,pr auc,threshold,f1",
                    "Prefer class weights or resampling, judge with PR-AUC rather than accuracy, and tune the decision threshold to the cost of errors.",
                    "Machine Learning,Statistics"),
            new Row("ML Engineer", "MLOps", Difficulty.MEDIUM,
                    "What belongs in a feature store?",
                    "reuse,consistency,train serve skew,versioning,point-in-time",
                    "Shared, versioned features computed once and served offline/online consistently, with point-in-time correctness to avoid leakage.",
                    "MLOps,Feature Store")
    );
}
