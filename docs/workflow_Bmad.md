# BMAD Method + Claude Code: Cẩm nang thực tế cho Developer Intern

## TL;DR
- **BMAD Method là một framework mã nguồn mở (MIT) mô phỏng cả một team agile bằng các AI agent chuyên biệt (Analyst, PM, Architect, Dev, QA, Scrum Master, UX, Tech Writer...), biến "vibe coding" hỗn loạn thành một quy trình spec-driven có artifact (PRD, Architecture, Stories) làm "single source of truth".** Với một intern, đây là công cụ mạnh để học cách làm phần mềm bài bản, NHƯNG nó nặng, tốn token, và dốc về learning curve.
- **Dùng BMAD khi: dự án greenfield phức tạp, có nhiều người dùng thật, tích hợp ngoài, hoặc yêu cầu compliance. KHÔNG dùng cho: sửa bug nhỏ, prototype, hoặc thay đổi <3 file** — lúc đó dùng Quick Flow của BMAD, plan mode của Claude Code, hoặc các tool nhẹ hơn như OpenSpec/Spec Kit.
- **Khuyến nghị cho intern: bắt đầu bằng `bmad-help` + Quick Dev, KHÔNG cài hết module, luôn mở chat mới cho mỗi workflow để tránh cạn context, và đọc kỹ mọi artifact agent tạo ra. Coi BMAD là cách "ép" bản thân suy nghĩ trước khi code, không phải nút "auto-magic".**

## Key Findings

1. **BMAD là một methodology framework, không phải thư viện code.** Nó là tập hợp agent definitions (file Markdown/YAML), workflow scripts và document templates mà bạn cài vào project và gọi qua một AI coding assistant như Claude Code. Agent là system prompt có persona, menu và memory — không phải process tự chạy.

2. **Vòng đời chia làm 4 phase: Analysis (optional) → Planning → Solutioning → Implementation.** Mỗi phase tạo ra artifact làm context cho phase sau. Đây là cốt lõi của "context engineering".

3. **Nguyên tắc vàng: mỗi agent tạo ra một artifact có thể verify, không chỉ là câu trả lời chat.** PRD, architecture doc, stories đều là file bền vững, version được, review được — handoff giữa các agent diễn ra qua FILE chứ không qua bộ nhớ chung.

4. **So với đối thủ: BMAD nặng nhất nhưng sâu nhất.** Spec Kit (GitHub) nhẹ và chuẩn cho team có sẵn quy trình; OpenSpec tốt nhất cho brownfield/legacy; GSD lean và chống "context rot"; Agent OS giờ chỉ lo "standards injection"; Aider/Plandex là coding agent terminal thuần, không phải SDD framework.

5. **Chi phí thật là vấn đề lớn.** BMAD ngốn nhiều token; cộng đồng phản ánh con số lớn về chi phí API hàng tháng và lượng token tiêu thụ. Đây là rào cản thực tế cho intern không có ngân sách API lớn.

6. **Cộng đồng chia rẽ rõ rệt:** người đến từ team agile có cấu trúc thì "không làm việc được nếu thiếu nó"; solo dev và team nhỏ thấy nó "overkill" và "kills the vibe".

## Details

### 1. BMAD Method — Deep Dive

#### Cơ chế hoạt động: agents, workflows, skills, personas
BMAD giải quyết 3 vấn đề cốt lõi của việc dùng một AI chat duy nhất cho cả dự án:
- **Context window exhaustion** — một cuộc hội thoại tích tụ quá nhiều context không liên quan, chất lượng giảm dần.
- **Role confusion** — cùng một AI cố làm PM, architect và developer cùng lúc, làm cái nào cũng kém.
- **No structured handoffs** — quyết định đầu cuộc hội thoại bị mất hoặc bị mâu thuẫn về sau.

Giải pháp của BMAD: **agent specialization + workflow isolation + document-driven handoffs**. Agent không chia sẻ memory hay lịch sử chat — chúng chia sẻ FILE: PM viết `PRD.md` → Architect đọc `PRD.md`, viết `architecture.md` → PM đọc cả hai, viết `epics/` → Dev đọc story file, viết code. Giống hệt nguyên tắc microservice contract: agent decoupled, document là API.

Trong BMAD v6, toàn bộ phương pháp được chuyển sang **kiến trúc dựa trên Skills** — mỗi agent là một skill được installer sinh ra, gọi qua skill ID (ví dụ `bmad-agent-dev`). Hệ sinh thái có 5 module chính thức:
- **BMM (BMad Method)** — module agile cốt lõi với nhiều workflow phủ analysis, planning, architecture, implementation.
- **BMB (BMad Builder)** — công cụ tạo agent/workflow/module riêng.
- **TEA (Test Architect)** — chiến lược test dựa trên rủi ro, vượt xa QA agent cơ bản.
- **BMGD (Game Dev Studio)** — workflow cho Unity/Unreal/Godot.
- **CIS (Creative Intelligence Suite)** — brainstorming và design thinking.

**V6 còn có 3 tầng kiến trúc:** BMad Core (framework cộng tác người-AI nền tảng), BMad Method (module agile xây trên Core), BMad Builder. Đặc điểm nổi bật của v6: Cross Platform Agent Team (cùng một cấu hình agent chạy trên Claude Code, Cursor, Codex... không cần config lại), Scale Adaptive workflows, và "step-file architecture" giúp tiết kiệm token đáng kể. Theo MarkTechPost (8/5/2026): *"Version 6.6.0 shipped on April 29, 2026, with the project reaching 46,700+ GitHub stars and more than 5,500 forks."*

#### Các agent quan trọng (BMM mặc định) và vai trò
Lưu ý: tên persona có thể khác nhau giữa các version/bản port. Bảng dưới theo docs chính thức v6:

| Agent | Tên persona | Skill ID | Vai trò cụ thể | Output chính |
|---|---|---|---|---|
| **Analyst** | Mary | `bmad-agent-analyst` | Nghiên cứu thị trường, cạnh tranh, domain, tính khả thi kỹ thuật; điểm vào của hầu hết dự án | `product-brief.md`, research findings, brainstorming report |
| **Product Manager** | John | `bmad-agent-pm` | Biến brief thành PRD với functional requirements (định dạng Given/When/Then) và NFR đo được; tạo epics & stories | `prd.md`, `addendum.md`, `decision-log.md` |
| **Architect** | Winston | `bmad-agent-architect` | Quyết định technical stack, infrastructure, API design; ghi lại dưới dạng ADR; chạy readiness gate | `architecture.md` với ADRs |
| **UX Designer** | Sally | `bmad-agent-ux-designer` | Thiết kế UX khi có UI | `DESIGN.md`, `EXPERIENCE.md` |
| **Scrum Master** | Bob | (trong workflow) | Lập sprint, chia epic thành story file giàu context với acceptance criteria | `story-[slug].md`, `sprint-status.yaml` |
| **Developer** | Amelia (hoặc James/Barry tùy bản) | `bmad-agent-dev` | Implement story bằng TDD, code review, quick dev | Working code + tests |
| **QA Engineer** | Quinn | (qua Dev agent) | Sinh test API/E2E nhanh, "ship it and iterate" | Test coverage |
| **Product Owner** | Sarah | (validation) | Align documents, shard spec lớn, giữ single source of truth | Validated docs |
| **Technical Writer** | Paige | `bmad-agent-tech-writer` | Viết doc, sinh Mermaid diagram, validate doc | Documentation |
| **Orchestrator / BMad Master** | — | `bmad-master` | Điều phối handoff, quản lý workflow state, party mode | — |

Quan trọng: **QA agent (Quinn) đơn giản hơn Test Architect (TEA module).** Quinn dùng để thêm test coverage nhanh ở Phase 4; còn code review/story validation thì dùng workflow CR của Dev agent, và chiến lược test cấp enterprise thì dùng module TEA riêng.

#### Workflow map: các phase, bước, output (theo docs chính thức)

**Phase 1 — Analysis (Optional):** Khám phá problem space.
- `bmad-brainstorming` → `brainstorming-report.md`
- `bmad-market-research`/`bmad-domain-research`/`bmad-technical-research` → research findings
- `bmad-product-brief` → `product-brief.md`
- `bmad-prfaq` → `prfaq-{project}.md` (kỹ thuật "Working Backwards" của Amazon)

**Phase 2 — Planning:** Định nghĩa CÁI GÌ cần xây.
- `bmad-prd` → `prd.md` + `decision-log.md` (Create/Update/Validate — 3 intent trong 1 skill). **Đây là bước BẮT BUỘC.**
- `bmad-ux` → `DESIGN.md` + `EXPERIENCE.md` (khi có UI)

**Phase 3 — Solutioning:** Định nghĩa XÂY NHƯ THẾ NÀO.
- `bmad-create-architecture` → `architecture.md` với ADRs (bắt buộc)
- `bmad-create-epics-and-stories` → epic files với stories (bắt buộc)
- `bmad-check-implementation-readiness` → quyết định PASS/CONCERNS/FAIL (bắt buộc — quality gate)

**Phase 4 — Implementation:** Xây từng story một.
- `bmad-sprint-planning` → `sprint-status.yaml`
- `bmad-create-story` → `story-[slug].md`
- `bmad-dev-story` → working code + tests (vòng lặp TDD: RED → GREEN → REFACTOR)
- `bmad-code-review` → adversarial review (approved hoặc changes requested)
- `bmad-correct-course` → xử lý thay đổi giữa sprint
- `bmad-retrospective` → lessons learned sau khi hoàn thành epic

**Quick Flow (track song song):** Bỏ qua phase 1-3 cho việc nhỏ, đã hiểu rõ. `bmad-quick-dev` → `spec-*.md` + code.

**Scale-Adaptive Intelligence:** BMAD tự điều chỉnh độ sâu planning theo độ phức tạp dự án, phân thành các level từ 0 (bug fix, 1 story) đến 4 (enterprise, 40+ stories). Có 3 planning track: **Quick Flow** (nhanh), **BMad Method** (đầy đủ), **Enterprise Method** (thêm tầng security + DevOps + test strategy).

#### Cách dùng với Claude Code: commands, skills, slash commands
- Cài: `npx bmad-method install` (cần Node.js v20+, Python 3.10+ và `uv` cũng được liệt kê là prerequisite trong các bản v6 gần đây). Chọn module BMM, chọn Claude Code làm IDE.
- Bản prerelease: `npx bmad-method@next install`.
- Gọi agent qua slash command, pattern dạng `/bmad-agent-bmm-pm`, `/bmad-agent-bmm-architect`, hoặc `/bmad:bmm:agents:dev`.
- Mỗi agent có menu trigger 2 loại: **workflow trigger** (gõ mã như `PRD`, `DS`, `CA`, `QD` — agent tự chạy workflow và hỏi input từng bước) và **conversational trigger** (cần mô tả thêm, ví dụ `WD Write a deployment guide`).
- **Web Bundles:** đóng gói skill để cài như Gemini Gems / ChatGPT Custom GPT. Dùng để làm phần planning (brainstorm, brief, PRD, UX) trên subscription web theo flat-rate thay vì đốt token IDE — tiết kiệm chi phí đáng kể cho engagement dài.

**⚠️ LỖI PHỔ BIẾN với Claude Code:** Đã có nhiều GitHub issue (ví dụ #773, #479, #1152) về việc slash command BMAD không hiện trong Claude Code vì Claude Code không quét command trong thư mục con của `.claude/commands/`, hoặc do cập nhật extension đổi cơ chế discovery (yêu cầu file manifest `commands.json`). Workaround: gõ tay đường dẫn command, hoặc dùng Claude Code CLI thay vì VSCode extension, hoặc cài đúng bản.

#### Greenfield vs Brownfield — khi nào dùng cái nào
- **Greenfield (dự án mới):** chạy clean pipeline: project brief → PRD → architecture → development. Đây là nơi BMAD tỏa sáng nhất.
- **Brownfield (codebase có sẵn):** BẮT BUỘC làm bước **Phase 0: `document-project`** trước — agent quét codebase (có 3 scan level) và sinh tài liệu AI-optimized vào `docs/`, có thể thay thế artifact Phase 1-2, cho phép brownfield vào thẳng Phase 3. Có 2 cách tiếp cận:
  - **PRD-First** (khuyến nghị cho codebase lớn): viết PRD trước, rồi chỉ document vùng liên quan.
  - **Document-First / Code-First** (cho project nhỏ): phân tích code trước rồi plan thay đổi.
- Brownfield phân loại scope: single story (<4 giờ), small feature (1-3 story), major enhancement (nhiều epic). **CẢNH BÁO:** Các GitHub issue (#446, #563) xác nhận giả định "documentation-first" của BMAD không map ngon lành lên monolith legacy 10 năm tuổi — đây là điểm yếu thực tế của BMAD trên brownfield messy.

#### bmad-help, party mode, quick-dev — là gì và dùng khi nào
- **`bmad-help`:** skill hướng dẫn thông minh. Nó kiểm tra project xem đã làm gì, hiển thị options theo module đã cài, và đề xuất bước tiếp theo. Tự động chạy ở cuối mỗi workflow. Hỏi tự nhiên kiểu: *"I just finished the architecture, what do I do next?"*. Một reviewer nhận xét nó "tồn tại chủ yếu để cứu bạn khỏi chính sự phức tạp của BMAD" — đúng nhưng vẫn rất hữu ích cho người mới.
- **Party Mode (`bmad-party-mode`):** đưa nhiều agent persona vào một session để thảo luận tập thể. BMad Master điều phối, chọn 2-3 agent liên quan mỗi message (không phải tất cả cùng lúc để tránh hỗn loạn), agent phản hồi đúng tính cách, đồng ý/phản đối/xây trên ý nhau. Dùng khi: cần Architect và PM tranh luận tradeoff, brainstorming, mô phỏng họp stakeholder, ra quyết định go/no-go. **CẢNH BÁO QUAN TRỌNG:** party mode đốt context và credit RẤT nhanh — một dev kể đã "vắt kiệt context window và credit" khi chạy party mode kết hợp nhiều công cụ search.
- **Quick Dev (`bmad-quick-dev`) / Quick Flow:** "fast lane" bỏ qua planning ceremony đầy đủ. Loads agent "Barry" (Elite Full-Stack Developer). Hai mode: từ tech-spec (Mode A) hoặc từ instruction trực tiếp (Mode B). Workflow: clarify intent → route to smallest safe path → execute → self-check → adversarial review → present. Dùng cho: bug fix, feature nhỏ, brownfield addition. Một dev đã xây xong game stickman Phaser 3 playable trong khoảng 2,5 giờ bằng Quick Dev. **Quy tắc:** nếu bạn thấy không thoải mái khi bỏ qua PRD + architecture doc, thì dùng full flow.

#### Lỗi phổ biến và best practices thực tế
**Best practices (từ docs + dev thực tế):**
- **Luôn mở chat MỚI cho mỗi workflow** — quy tắc quan trọng nhất. Context window đầy → chất lượng giảm. Artifact trên disk mang state giữa các workflow.
- **Tạo `project-context.md`** — như "hiến pháp" của project, load tự động bởi mọi implementation workflow. Giữ ngắn gọn, chỉ ghi cái "không hiển nhiên" (ví dụ "dùng Zustand không dùng Redux"), không ghi best practice phổ quát.
- **Dùng Web Bundles cho planning** để tiết kiệm token IDE.
- **Cài ít thôi** — chỉ BMM Core, đừng cài hết module một lúc.
- Khi output bị skip chương hoặc điền text generic → bảo agent chia nhỏ câu trả lời.

**Pitfalls phổ biến:**
- **Handoff failures giữa agent:** khi Architect giả định một điều mà PM không document, Scrum Master truyền nó vào story, Dev implement tự tin → bạn phát hiện ở QA hoặc tệ hơn là production. "Pipeline chỉ tốt bằng cái handoff yếu nhất."
- **Agent đánh dấu story "complete" nhưng feature vẫn hỏng** — đã có report mất hơn 9 giờ với hệ thống authentication không hoạt động mà pipeline báo xong.
- **Adversarial code review ép tối thiểu 3 issue** (GitHub issue #1332) → tạo nitpick giả, vòng review vô tận, mệt mỏi dev.
- **Quick Dev bỏ qua step khi truyền tech-spec trực tiếp** (issue #1725) → mất self-check và adversarial review. Fix: chỉ gõ `QD` rồi truyền spec sau.
- **Đốt token vô tội vạ** với party mode + model lớn.

### 2. So sánh BMAD với đối thủ

| Tiêu chí | BMAD | GitHub Spec Kit | OpenSpec | GSD | Agent OS | Claude Code (thuần) | Aider | Plandex |
|---|---|---|---|---|---|---|---|---|
| **Bản chất** | Multi-agent SDLC framework | Spec-first toolkit 4 phase | Delta-spec, brownfield-first | Lean meta-prompting | Standards injection | Coding agent | Pair-programming CLI | Terminal agent cho repo lớn |
| **Độ nặng** | Nặng nhất | Trung bình | Nhẹ | Rất nhẹ | Nhẹ | Nhẹ | Nhẹ | Trung bình |
| **Workflow** | Analysis→Planning→Solutioning→Implementation | specify→plan→tasks→implement | proposal→review→implement→archive | researcher/planner/executor/verifier song song | discover→inject→shape spec | plan mode tự nhiên | chat trực tiếp | long-running plan |
| **GitHub stars (giữa 2026)** | 46,700+ (v6.6.0, 29/4/2026) | 108k (v0.8.18, 29/5/2026) | 18k+ (theo maintainer Fission-AI) | 61,000+ (từ ~12/2025) | — | — | — | — |
| **Tốt nhất cho** | Greenfield phức tạp, compliance | Chuẩn hóa AI quality cho team có sẵn | Legacy/brownfield refactor | Solo dev ship nhanh | Inject coding standards | Task nhỏ, multi-file 2-3 session | Sửa 1 file nhanh | Refactor monorepo 20M+ token |

**GitHub Spec Kit** (GitHub, ra cuối 2025): workflow 4 phase `specify → plan → tasks → implement` với "constitution" toàn project mà mọi spec kế thừa. Hỗ trợ **29 named integrations + 1 generic** (Claude Code, GitHub Copilot, Gemini CLI, Cursor, Windsurf, Codex CLI...); với Claude Code dùng tích hợp skills-based đặt file dưới `.claude/skills/`. Đây là framework phổ biến nhất về star: **108k stars, 9.5k forks** (release v0.8.18, 29/5/2026). **Mạnh:** distribution mạnh nhất, deep Copilot integration, dễ học (1-2 ngày), rigor có gate. **Yếu:** không có bước code review built-in, đổi hướng phải re-run cả command (regenerate cả document, không có diff phần thay đổi), code đôi khi không map đúng intent spec. **Dùng khi:** team có sẵn quy trình, feature vừa, cần chuẩn hóa AI quality. **Không dùng khi:** dự án multi-service lớn (spec drift gây vấn đề coordination).

**OpenSpec** (`@fission-ai/openspec`): proposal-first, dùng delta spec (chỉ ghi cái ĐANG thay đổi, ADDED/MODIFIED). **18k+ stars** (theo maintainer Fission-AI). **Mạnh:** nhẹ nhất, token-efficient (spec ~250 dòng vs ~800 dòng của Spec Kit), audit trail qua proposal archive, `openspec validate --strict` bắt lỗi missing scenario, IDE integration giàu nhất (cài sẵn trên 24 tool), upgrade path sạch nhất. **Yếu:** lock-in (cố định nơi spec sống, CLI khó bend), multi-agent orchestration hạn chế, do 1 người maintain. **Dùng khi:** brownfield/legacy, môi trường bắt buộc document thay đổi và review trước khi code. **Không dùng khi:** sáng kiến multi-service lớn cần role specialization sâu.

**GSD (Get Shit Done)** by TACHES: sinh ra như phản ứng chống lại sự phức tạp của BMAD/Spec Kit. Triết lý: "complexity should live in the system, not the workflow." **61,000+ stars, tăng từ 0 trong chưa đầy 5 tháng kể từ commit đầu 12/2025** (theo MarkTechPost). Cài `npx get-shit-done-cc`. Mỗi plan tối đa 3 task, chạy trong sub-agent với context sạch 200K token, mỗi task là 1 commit revertable, "zero context rot". Hỗ trợ 14+ runtime kể cả local model. **Mạnh:** lean, nhanh, rẻ, chống context rot, model-agnostic. **Yếu:** multi-agent orchestration hạn chế — nếu cần role specialization thật (PM/Architect/Dev) thì leanness thành trần. **Dùng khi:** solo dev, iteration nhanh trên project nhỏ-vừa, requirement linh hoạt. **Không dùng khi:** cần planning sâu và audit trail.

**Agent OS** (Builder Methods, Brian Casel): từ v3 đã thu hẹp phạm vi — KHÔNG còn cố tự viết spec/task breakdown (giao cho Plan Mode của Claude Code), chỉ tập trung vào **"standards injection"**: discover standards từ codebase → inject đúng standard vào đúng lúc → shape spec tốt hơn (`/shape-spec`). **Mạnh:** nhẹ, làm việc CÙNG tool hiện đại thay vì thay thế, giữ codebase consistency, không reinvent. **Yếu:** không phải full SDLC framework, không multi-agent orchestration. **Dùng khi:** muốn AI tuân thủ convention/standards của codebase, làm cùng Claude Code plan mode. **Không dùng khi:** cần quy trình planning đầy đủ end-to-end.

**Claude Code standalone (không framework):** dùng plan mode + `CLAUDE.md`. **Mạnh:** không overhead, nhanh, linh hoạt, frontier model tự quản todo list và delegate subagent. **Yếu:** không persistent spec (drift trong vài giờ), `CLAUDE.md` có thể bị bỏ qua (đã có case agent nhận ra mình vi phạm workflow nhưng vẫn commit), context exhaustion nhanh (có case đầy context trong ~10 phút), không multi-agent coordination. **Dùng khi:** task 1 file, bug fix, CRUD đơn giản, hoặc feature multi-file 2-3 session với `CLAUDE.md` gọn. **Không dùng khi:** cần multi-agent coordination, drift detection, hoặc spec cập nhật tự động khi code tiến triển.

**Aider:** pair-programming CLI với LLM, edit trực tiếp file trong git repo và auto-commit kèm commit message. **Mạnh:** Architect/Editor mode (ghép model reasoning đắt với model edit rẻ để tối ưu chi phí), repomap (tree-sitter 100+ ngôn ngữ), auto-commit từng thay đổi, Apache 2.0, ~$5-15/ngày trên Claude Sonnet với prompt caching. **Yếu:** không IDE plugin, không phải SDD framework (không lo spec/planning), context giới hạn trên repo cực lớn. **Dùng khi:** sửa nhanh multi-file hằng ngày, muốn kiểm soát chi phí và git history rõ. **Không dùng khi:** cần planning có cấu trúc hoặc repo cực lớn.

**Plandex:** terminal agent MIT-license cho task lớn nhiều file. **Mạnh:** tree-sitter project map index codebase 20M+ token (chỉ load cái cần — xử lý monorepo blow past Aider/Cursor), cumulative diff sandbox (giữ thay đổi ngoài working tree đến khi accept từng file, full rollback), mix model theo role, REPL mode. **Yếu:** Plandex Cloud đã đóng cửa 7/11/2025 (giờ phải self-host Docker), learning curve dốc hơn Aider, không IDE plugin, không phải SDD framework. **Dùng khi:** refactor monorepo scale lớn, cần sandbox review an toàn. **Không dùng khi:** sửa 1 file nhanh, hoặc muốn cài đặt zero-config.

**Điểm chung quan trọng:** Aider và Plandex là **coding agent** (lo việc viết/sửa code), còn BMAD/Spec Kit/OpenSpec/GSD/Agent OS là **methodology/SDD framework** (lo việc plan + spec). Bạn thậm chí có thể chạy methodology framework (BMAD) ở trên một coding agent.

### 3. Thực tế áp dụng BMAD step-by-step

**Bắt đầu dự án mới greenfield với BMAD + Claude Code:**
1. `npx bmad-method install` (Node 20+), chọn module BMM, chọn Claude Code. Restart Claude Code.
2. Gõ `bmad-help` ngay → để nó detect module và hướng dẫn điểm bắt đầu.
3. **(Optional) Phase 1:** Load Analyst → brainstorm + `product-brief.md`. Cân nhắc làm trên Web Bundle (ChatGPT/Gemini) để tiết kiệm token.
4. **Phase 2:** Load PM → `/bmad-bmm-create-prd` → `PRD.md` (BẮT BUỘC). Nếu có UI → tạo UX design.
5. **(Khuyến nghị) Tạo `project-context.md`** trước architecture nếu bạn có technical preference mạnh.
6. **Phase 3:** Load Architect → `/bmad-bmm-create-architecture` → `architecture.md`. Sau đó `/bmad-bmm-create-epics-and-stories`. Tại sao sau architecture? Vì quyết định database/API trực tiếp xác định cách chia story.
7. Chạy `/bmad-bmm-check-implementation-readiness` → validate cohesion giữa các doc (PASS/CONCERNS/FAIL).
8. **Phase 4 (lặp từng story):** SM `/bmad-bmm-create-story` → Dev `/bmad-bmm-dev-story` → Dev `/bmad-bmm-code-review`. Sau khi xong epic → `/bmad-bmm-retrospective`.
9. **Quy tắc sống còn:** mở chat MỚI cho mỗi workflow.

**Artifact cần tạo:** `product-brief.md` (optional), `PRD.md` (bắt buộc), `DESIGN.md`/`EXPERIENCE.md` (nếu có UI), `architecture.md` với ADR (bắt buộc), `project-context.md` (khuyến nghị), epic + story files (bắt buộc), `sprint-status.yaml`, working code + tests.

**Quản lý context window:**
- Mỗi workflow = một chat mới.
- Document trên disk là cách carry state, không phải lịch sử chat.
- Shard tài liệu lớn (PRD, Architecture) thành nhiều file con với `index.md` (dùng `shard-doc`).
- Khi dùng Gemini/model context nhỏ: copy-paste bước tiếp theo vào prompt để tránh agent "nhảy bước".
- Quick Dev có giới hạn: nếu spec sinh ra vượt ~1600 token, agent phải halt và hỏi bạn chia nhỏ task hay chấp nhận rủi ro context rot.

**Tips cho team:**
- Pin team install settings ở central config (`_bmad/custom/config.toml`), giữ personal settings (user_name, language) ở `config.user.toml` riêng.
- PR mọi thay đổi command file để team đồng bộ.
- Chọn một "Planner of Record" nếu dùng song song công cụ khác — đừng chạy 2 planner cùng lúc.
- Customize agent qua `.customize.yaml` để áp convention (ví dụ Dev agent luôn dùng Context7 MCP cho library docs).

**Community feedback thực tế:**
- **Người yêu thích:** Trên Hacker News (thread "The Claude Code Framework Wars"), user *matt3D* nói BMAD "by far the best Claude Code compliment" và "completely transformative" vì tạo document chuẩn hóa làm memory vượt context window; user *g42gregory* nói "night and day... Can't work without it." Thường là dev đến từ team có cấu trúc, làm greenfield lớn/compliance.
- **Người thấy overkill:** Solo dev và team 2-3 người. Một dev (willtorber, DEV Community) nói với startup 2 người "it's a trap... BMAD is a process multiplier, not a process creator — nếu team bạn không có quy trình, BMAD sẽ reproduce sự hỗn loạn của bạn qua bảy agent." Trên HN, user *grim_io* gọi các framework này nghiêng về "snake oil... A lot of process and ritual around using them, but for what?".
- **Middle ground (phổ biến nhất):** đáng dùng cho feature multi-file và quyết định design thật; overkill cho fix 1 dòng. Một dev (bspann, DEV Community) viết: "for small stuff (a typo fix, a CSS tweak, a one-line config change) the full BMAD workflow is overkill. I skip it for anything that touches fewer than three files or doesn't involve a real design decision" — nhưng cũng nói "I don't build without it now".
- **Con số chi phí:** Theo Anderson Santos ("You should BMAD — part 2", Medium): *"One analysis showed that earlier versions averaged approximately 31,667 tokens per workflow run, with monthly API costs reaching $847 for example projects (Trần, 2025)."* Cùng bài này dẫn nguồn (Reddit, 2025a) cho con số ~230 triệu token/tuần trên dự án lớn và vụ authentication hỏng 9 giờ. Learning curve ước tính ~2 tháng để master (so với 1-2 ngày cho Spec Kit).
- **Phê bình từ chính cộng đồng BMAD:** GitHub issue #2003 ("Structural Gaps and Contradictions of BMAD v6 Stable") lập luận BMAD "giao quyết định quan trọng cho user không được train, mà thiếu safeguard" và một MVP nhỏ "có thể tốn 10-15 lần thời gian so với dùng Claude Code/Codex thuần".

### 4. Kết luận thực tế

**BMAD phù hợp nhất với:** dự án greenfield phức tạp; sản phẩm có người dùng thật, tích hợp ngoài, hoặc bề mặt bảo mật; dự án yêu cầu compliance/audit (SOC2, healthcare, finance); team đã quen tư duy PRD/architecture/sprint story; solo dev muốn "mô phỏng" cả một team. Giá trị của SDD tỉ lệ thuận với độ bền của artifact nó tạo ra — spec mà agent thực sự đọc, sống sót qua session reset, và tiến hóa cùng code thì mới đáng viết.

**KHÔNG nên dùng BMAD khi:** prototype/MVP cần validate nhanh; sửa bug nhỏ hoặc thay đổi <3 file; requirement thay đổi liên tục (phase-gating tạo ma sát); chỉ có model nhỏ/context hạn chế (PRD + architecture đã ngốn hàng chục nghìn token); ngân sách API eo hẹp; brownfield monolith legacy cực messy; use case không phải lập trình (content, automation).

## Recommendations

**Cho một junior developer mới bắt đầu (lộ trình staged):**

1. **Tuần 1 — Làm quen, đừng tham.** Cài `npx bmad-method install`, chỉ chọn module **BMM**, chọn Claude Code. Chạy `bmad-help` đầu tiên. Làm một project nhỏ end-to-end bằng **Quick Dev** trước (không phải full flow) để hiểu cảm giác spec → code → review. Đây là cách rẻ và nhanh nhất để học.

2. **Tuần 2-3 — Lên full flow trên một feature thật.** Chọn một feature chạm 3-5 file, có design decision thật. Chạy qua đủ Phase 2 (PRD) → Phase 3 (Architecture + Stories + Readiness check) → Phase 4. **Mở chat mới cho mỗi workflow.** Tạo `project-context.md` gọn. Đọc KỸ mọi artifact agent tạo — đừng rubber-stamp.

3. **Luôn áp dụng:** dùng Web Bundles (ChatGPT/Gemini subscription) cho phần planning để tiết kiệm token IDE; review adversarial code review một cách hoài nghi (nó bị ép tìm tối thiểu 3 issue → nhiều cái là nitpick); dùng retrospective khi bị kẹt thay vì cố push tới.

4. **Cẩn thận với:** party mode (đốt context/credit nhanh — dùng có mục đích, ngắn); chi phí token (theo dõi usage, ưu tiên dùng gói flat-rate như Claude Max nếu có); handoff giữa agent (thêm checklist handoff thủ công nếu thấy Architect giả định sai PRD).

**Benchmark/ngưỡng đổi quyết định:**
- Nếu task **<3 file hoặc không có design decision** → bỏ BMAD, dùng plan mode Claude Code hoặc Quick Dev.
- Nếu **brownfield legacy messy** và BMAD vật lộn → cân nhắc OpenSpec (delta-spec) thay thế.
- Nếu **chi phí API/tháng vượt ngân sách** → chuyển planning lên Web Bundle, hoặc dùng GSD (lean hơn).
- Nếu **team chưa có quy trình** → đừng kỳ vọng BMAD tạo ra quy trình; xây thói quen spec trước bằng tool nhẹ rồi mới scale lên BMAD.
- Nếu thấy mình **dành nhiều thời gian debug agent coordination hơn là viết code** → đó là tín hiệu BMAD đang overkill cho dự án này.

## Caveats
- **Tên persona và skill ID khác nhau giữa version và các bản port (community forks).** Bài viết dùng docs chính thức v6; một số nguồn liệt kê tên khác (James/Barry cho Dev, Preston/Devon/Simon...). Luôn kiểm tra menu agent thực tế trong project của bạn.
- **Con số chi phí token và "tiết kiệm 70-90%" phần lớn đến từ bài blog của người ủng hộ/marketing, không phải benchmark độc lập** — coi là claim, không phải sự thật đã verify. Con số phê bình (230M token/tuần, vụ auth hỏng 9 giờ) đến từ thread Reddit nhưng được trích qua nguồn thứ cấp (bài Medium của Anderson Santos), không phải nguyên văn từ Reddit.
- **GitHub stars và version thay đổi nhanh** — các framework này đều ship major version trong vài tháng gần đây (BMAD v6.6.0 ~29/4/2026, Spec Kit v0.8.18 ~29/5/2026, GSD v1.40.0...). Số liệu trong báo cáo là snapshot giữa 2026.
- **BMAD đang chuyển mạnh sang Skills architecture và Dev Loop Automation** — workflow Phase 4 đang được tự động hóa thêm, Quick Dev mới đang ở bản preview thử nghiệm. Cơ chế có thể đổi.
- Một số lỗi tương thích Claude Code (slash command không hiện) phụ thuộc phiên bản extension cụ thể tại thời điểm cài.