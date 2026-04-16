--
-- PostgreSQL database dump
--

\restrict PkcM7HrmfwBaLJKHK9A3tEeGuVWeeYeSvCwhHATW63MhhN0V0d72jwEud8b5kgL

-- Dumped from database version 15.15
-- Dumped by pg_dump version 15.15

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

DROP INDEX IF EXISTS public.idx_users_email;
ALTER TABLE IF EXISTS ONLY public.users DROP CONSTRAINT IF EXISTS users_pkey;
ALTER TABLE IF EXISTS ONLY public.users DROP CONSTRAINT IF EXISTS users_email_key;
ALTER TABLE IF EXISTS public.users ALTER COLUMN id DROP DEFAULT;
DROP SEQUENCE IF EXISTS public.users_id_seq;
DROP TABLE IF EXISTS public.users;
SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: users; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    email character varying(255) NOT NULL,
    password character varying(255) NOT NULL,
    nickname character varying(64) DEFAULT '微信用户'::character varying NOT NULL,
    avatar character varying(512) DEFAULT ''::character varying,
    gender smallint DEFAULT 0,
    birthday date,
    region character varying(100),
    bio character varying(255) DEFAULT ''::character varying,
    role character varying(20) DEFAULT 'USER'::character varying,
    status integer DEFAULT 1,
    is_deleted integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    token_version integer DEFAULT 0 NOT NULL,
    password_changed_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


ALTER TABLE public.users OWNER TO postgres;

--
-- Name: TABLE users; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.users IS '用户核心表';


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.users_id_seq OWNER TO postgres;

--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.users (id, email, password, nickname, avatar, gender, birthday, region, bio, role, status, is_deleted, created_at, updated_at, token_version, password_changed_at) FROM stdin;
11	test2@szu.edu.cn	$2a$10$AnA40ijXKAiX0iUgxjBwS.e9TMaP5m8LPALBfjCebO9H1zy/vOTuK	冒烟测试员	https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg	0	\N	\N		USER	1	0	2025-12-01 10:33:21.270698	2025-12-01 10:33:21.270698	0	2026-01-21 19:40:36.592255
13	garhlz257@163.com	123456	test	https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg	0	\N	\N		USER	1	0	2025-12-01 11:05:49.037042	2025-12-01 11:05:49.037042	0	2026-01-21 19:40:36.592255
15	gztmft_fgo84@vip.qq.com	$2a$10$5OBj/Pc4HwtabDFb0/KdfeAXW5A8oB7LbsfNNO.HgGXwVHB1h7awu	杭梓馨	https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg	0	\N	\N		USER	1	0	2025-12-03 19:46:59.389696	2025-12-03 19:46:59.392145	0	2026-01-21 19:40:36.592255
16	test4@test.com	$2a$10$aAG828PAonJ4VPsyNiML.uup9B2pPbgLQhZT5xJMCZ.ZPJB6SUR9m	杭梓馨	https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg	0	\N	\N		USER	1	0	2025-12-03 19:47:21.598345	2025-12-03 19:47:21.598424	0	2026-01-21 19:40:36.592255
276	ai_bot@szu.edu.cn	random_password_cannot_login	AI省流助手	https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg	0	\N	\N		ADMIN	1	0	2025-12-12 19:52:43.905878	2025-12-12 19:52:43.905878	0	2026-01-21 19:40:36.592255
283	photo@test.com	$2a$10$rT/xOkHVCfdPbi31IvJhkeJs457F.fUy4kmfXZFmelNQB4mLAOj0q	PhotoLife	https://api.dicebear.com/7.x/avataaars/svg?seed=PhotoLife	0	\N	\N	用镜头记录生活的美好	USER	1	0	2025-12-13 14:48:30.922389	2025-12-13 14:48:30.922441	0	2026-01-21 19:40:36.592255
275	admin1@test.com	$2a$10$CcO2oC3cPY7p0yTbKRkSKuEIdYfRfGOGyw2to1QP9TgjK9FDzAHvS	小沁	https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/uploads/2026/01/29/e11857d9f4ef471ea2e59deb1bbf2d7e.jpg	0	2018-09-01	\N	你好哇！我是小沁！！	ADMIN	1	0	2025-12-12 17:34:23.700905	2025-12-12 17:34:23.702853	0	2026-01-21 19:40:36.592255
282	tech@test.com	$2a$10$aBCvOAfUkCw6AldeO4RC7O5WV3I26feydsKS3TFcduD9pafa.1Qa6	TechLover	https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/uploads/2026/01/29/10595164385e4b9aa03353698c4d6e46.jpg	2	2005-06-07	深圳	hello, world!	USER	1	0	2025-12-13 14:48:30.802542	2025-12-13 14:48:30.804403	2	2026-01-29 23:22:03.978008
285	mike@test.com	$2a$10$FM/4.Li8JNKbAVBKPifgF.s8kl7tLFlEGYof84mVnfTMLPs/MF9sK	StudentMike	https://api.dicebear.com/7.x/avataaars/svg?seed=StudentMike	0	\N	\N	大三学生，正在准备考研	USER	1	0	2025-12-13 14:48:31.122688	2025-12-13 14:48:31.122721	0	2026-01-21 19:40:36.592255
288	Eglantine235711@gmail.com	$2a$10$ftX1W1uz0r0hcTCbUl.dJOlr4wf1FtC801dIc17MO0eDhZJbipu4i	测试账号	https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg	0	\N	\N		USER	1	0	2026-01-22 18:41:27.687539	2026-01-22 18:41:27.687539	4	2026-01-22 23:57:23.605976
289	garhlz257@gmail.com	$2a$10$llXpUqCQUe6EHSw4MprWteoT1RIVN1HgHw85M1OOizTnKhHSQdG92	Eglantine	https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg	0	\N	\N		USER	1	0	2026-01-23 20:31:15.617273	2026-01-23 20:31:15.617273	1	2026-01-23 20:32:50.00936
17	test5@test.com	$2a$10$iMN1NCwC2F8t3t3DyS6GOO.Hw3kIHlccMY7HPds98duOEtgsaDtLy	eglntn	https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/uploads/2026/01/29/533a01f1664d463f888658d319e70af9.jpg	0	\N	其他	国家一级退堂鼓表演艺术家 🥁。主业：在 Deadline 边缘疯狂试探；副业：制造 Bug 并假装没看见 🐛。梦想是不劳而获（划掉）财富自由 💰。关注我，一起快乐摸鱼 🐟。	USER	1	0	2025-12-03 20:03:01.553754	2025-12-03 20:03:01.555836	0	2026-01-21 19:40:36.592255
284	jane@test.com	$2a$10$lD5vuQXBUiPF2JniDNONfeS8wlRraFM92VwyQhNOlPDWfBl1uUcyO	FoodieJane	https://api.dicebear.com/7.x/avataaars/svg?seed=FoodieJane	0	1998-02-27	深圳	探店达人 | 寻找城市角落的美味	USER	1	0	2025-12-13 14:48:31.021005	2025-12-13 14:48:31.021044	0	2026-01-21 19:40:36.592255
286	cat@test.com	$2a$10$6Pbfc3qj6iuCLc.9iFog4.UyKxzVaJlUWAWnUQt6hlL53Qm6aoBTi	CatLover	https://api.dicebear.com/7.x/avataaars/svg?seed=CatLover	1	1980-06-12	\N	猫奴一枚，只有猫猫能治愈我	USER	1	0	2025-12-13 14:48:31.225087	2025-12-13 14:48:31.225124	0	2026-01-21 19:40:36.592255
\.


--
-- Name: users_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.users_id_seq', 289, true);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: idx_users_email; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_users_email ON public.users USING btree (email);


--
-- PostgreSQL database dump complete
--

\unrestrict PkcM7HrmfwBaLJKHK9A3tEeGuVWeeYeSvCwhHATW63MhhN0V0d72jwEud8b5kgL

