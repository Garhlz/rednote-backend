--
-- PostgreSQL database dump
--
-- Dumped from database version 18.1
-- Dumped by pg_dump version 18.1
    
--
-- Name: users; Type: TABLE; Schema: public; Owner: elaine
--

CREATE TABLE public.users (
                              id bigint NOT NULL,
                              openid character varying(64),
                              email character varying(255),
                              password character varying(255),
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
                              updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);



--
-- Name: TABLE users; Type: COMMENT; Schema: public; Owner: elaine
--

COMMENT ON TABLE public.users IS '用户核心表';


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: elaine
--

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: elaine
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: elaine
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: elaine
--

COPY public.users (id, openid, email, password, nickname, avatar, gender, birthday, region, bio, role, status, is_deleted, created_at, updated_at) FROM stdin;
9	openid_1764531045463	user_1764531045463@szu.edu.cn	123456	杜沐阳	https://avatars.githubusercontent.com/u/68831312	1	2024-12-10	华南	钟爱好者，玩家🇩🇯	USER	1	0	2025-12-01 03:30:45.463563	2025-12-01 03:30:45.463583
86	\N	e0f7bf93@test.com	\N	Sender_XiaoQing		0	\N	\N		USER	1	1	2025-12-09 10:07:45.8232	2025-12-09 10:07:48.307369
87	\N	3ee8c8b2@test.com	\N	Receiver_YouYou		0	\N	\N		USER	1	1	2025-12-09 10:07:46.150241	2025-12-09 10:07:48.315533
18	\N	inter_test@szu.edu.cn	\N	交互测试员		0	\N	\N		USER	1	1	2025-12-08 18:30:51.123335	2025-12-08 18:30:55.695684
88	\N	4fa81b90@test.com	\N	Sender_XiaoQing		0	\N	\N		USER	1	1	2025-12-09 10:07:48.383665	2025-12-09 10:07:50.122011
89	\N	bf875615@test.com	\N	Receiver_YouYou		0	\N	\N		USER	1	1	2025-12-09 10:07:48.392551	2025-12-09 10:07:50.126164
4	openid_1764530857387	\N	\N	TestUser_1764530857387	https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg	0	\N	\N		USER	1	0	2025-12-01 03:27:37.387274	2025-12-01 03:27:37.387297
5	openid_1764530902698	\N	\N	TestUser_1764530902698	https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg	0	\N	\N		USER	1	0	2025-12-01 03:28:22.698948	2025-12-01 03:28:22.698981
6	openid_1764530941996	\N	\N	TestUser_1764530941996	https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg	0	\N	\N		USER	1	0	2025-12-01 03:29:01.996711	2025-12-01 03:29:01.996751
7	openid_profile_1764530961500	profile_1764530961500@test.com	encrypted_pwd	ProfileTest_61500	https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg	1	2000-01-01	Shenzhen	Hello World	USER	1	0	2025-12-01 03:29:21.500474	2025-12-01 03:29:21.500494
8	openid_1764531005451	\N	\N	TestUser_1764531005451	https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg	0	\N	\N		USER	1	0	2025-12-01 03:30:05.451362	2025-12-01 03:30:05.451385
47	\N	search_test_1765209387954@szu.edu.cn	\N	搜索测试员		0	\N	\N		USER	1	1	2025-12-08 23:56:27.956174	2025-12-08 23:56:27.956296
10	openid_1764531098235	szu_test_1764531098235@163.com	$2a$10$/KcgDT4JIQgeTWJUlax/AuQW5P4I49zhKfOI8KB6.7MnC4ASSEBXe	UpdatedName	https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg	2	2003-05-20	\N	This is a bio	USER	1	0	2025-12-01 03:31:38.235762	2025-12-01 03:31:38.235784
11	smoke_test_openid_2	test2@szu.edu.cn	$2a$10$AnA40ijXKAiX0iUgxjBwS.e9TMaP5m8LPALBfjCebO9H1zy/vOTuK	冒烟测试员	https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg	0	\N	\N		USER	1	0	2025-12-01 10:33:21.270698	2025-12-01 10:33:21.270698
13	openid_1764531098234	garhlz257@163.com	123456	test	https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg	0	\N	\N		USER	1	0	2025-12-01 11:05:49.037042	2025-12-01 11:05:49.037042
14	\N	test_consistency@szu.edu.cn	\N	新名字_New	https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg	0	\N	\N		USER	1	1	2025-12-03 14:14:18.523271	2025-12-03 14:14:21.081291
15	test_openid_1764762419383	gztmft_fgo84@vip.qq.com	$2a$10$5OBj/Pc4HwtabDFb0/KdfeAXW5A8oB7LbsfNNO.HgGXwVHB1h7awu	杭梓馨	https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg	0	\N	\N		USER	1	0	2025-12-03 19:46:59.389696	2025-12-03 19:46:59.392145
16	test_openid_1764762441597	test4@test.com	$2a$10$aAG828PAonJ4VPsyNiML.uup9B2pPbgLQhZT5xJMCZ.ZPJB6SUR9m	杭梓馨	https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg	0	\N	\N		USER	1	0	2025-12-03 19:47:21.598345	2025-12-03 19:47:21.598424
228	\N	search_test_1765456414011@szu.edu.cn	\N	搜索测试员		0	\N	\N		USER	1	1	2025-12-11 20:33:34.114397	2025-12-11 20:33:34.117126
229	\N	search_test_1765456414990@szu.edu.cn	\N	搜索测试员		0	\N	\N		USER	1	1	2025-12-11 20:33:34.99145	2025-12-11 20:33:34.991493
230	\N	search_test_1765456415077@szu.edu.cn	\N	搜索测试员		0	\N	\N		USER	1	1	2025-12-11 20:33:35.078099	2025-12-11 20:33:35.078154
231	\N	search_test_1765456415140@szu.edu.cn	\N	搜索测试员		0	\N	\N		USER	1	1	2025-12-11 20:33:35.140978	2025-12-11 20:33:35.14102
232	\N	mq_test_f5c2b5@test.com	\N	新名字_NewName_MQ		0	\N	\N		USER	1	1	2025-12-11 20:35:15.196691	2025-12-11 20:35:17.779526
45	\N	search_test_1765209387240@szu.edu.cn	\N	搜索测试员		0	\N	\N		USER	1	1	2025-12-08 23:56:27.331767	2025-12-08 23:56:27.334341
46	\N	search_test_1765209387828@szu.edu.cn	\N	搜索测试员		0	\N	\N		USER	1	1	2025-12-08 23:56:27.830138	2025-12-08 23:56:27.830245
48	\N	search_test_1765209503724@szu.edu.cn	\N	搜索测试员		0	\N	\N		USER	1	1	2025-12-08 23:58:23.796577	2025-12-08 23:58:23.798783
49	\N	search_test_1765209505231@szu.edu.cn	\N	搜索测试员		0	\N	\N		USER	1	1	2025-12-08 23:58:25.232586	2025-12-08 23:58:25.232675
50	\N	search_test_1765209505343@szu.edu.cn	\N	搜索测试员		0	\N	\N		USER	1	1	2025-12-08 23:58:25.345091	2025-12-08 23:58:25.345171
51	\N	search_test_1765209571696@szu.edu.cn	\N	搜索测试员		0	\N	\N		USER	1	1	2025-12-08 23:59:31.7917	2025-12-08 23:59:31.794306
52	\N	search_test_1765209573241@szu.edu.cn	\N	搜索测试员		0	\N	\N		USER	1	1	2025-12-08 23:59:33.242126	2025-12-08 23:59:33.242174
53	\N	search_test_1765209573323@szu.edu.cn	\N	搜索测试员		0	\N	\N		USER	1	1	2025-12-08 23:59:33.324932	2025-12-08 23:59:33.325021
54	\N	search_test_1765209800055@szu.edu.cn	\N	搜索测试员		0	\N	\N		USER	1	1	2025-12-09 00:03:20.13468	2025-12-09 00:03:20.136802
55	\N	search_test_1765209801605@szu.edu.cn	\N	搜索测试员		0	\N	\N		USER	1	1	2025-12-09 00:03:21.606874	2025-12-09 00:03:21.606958
56	\N	search_test_1765209801698@szu.edu.cn	\N	搜索测试员		0	\N	\N		USER	1	1	2025-12-09 00:03:21.699232	2025-12-09 00:03:21.699325
57	\N	search_test_1765209968463@szu.edu.cn	\N	搜索测试员		0	\N	\N		USER	1	1	2025-12-09 00:06:08.539106	2025-12-09 00:06:08.541616
58	\N	search_test_1765209970037@szu.edu.cn	\N	搜索测试员		0	\N	\N		USER	1	1	2025-12-09 00:06:10.038701	2025-12-09 00:06:10.038758
59	\N	search_test_1765209970095@szu.edu.cn	\N	搜索测试员		0	\N	\N		USER	1	1	2025-12-09 00:06:10.095746	2025-12-09 00:06:10.095795
60	\N	search_test_1765209999150@szu.edu.cn	\N	搜索测试员		0	\N	\N		USER	1	1	2025-12-09 00:06:39.261899	2025-12-09 00:06:39.265503
61	\N	search_test_1765210376169@szu.edu.cn	\N	搜索测试员		0	\N	\N		USER	1	1	2025-12-09 00:12:56.237617	2025-12-09 00:12:56.241288
282	\N	tech@test.com	$2a$10$lN0gLpoReo5XOXsVJRL8BehZe7.xfNrsVDtbd0V1.YQmZLjOmUEeC	TechGeek	https://api.dicebear.com/7.x/avataaars/svg?seed=TechGeek	0	\N	\N	热爱代码，探索前沿科技	USER	1	0	2025-12-13 14:48:30.802542	2025-12-13 14:48:30.804403
258	\N	a96a2ae8@test.com	\N	Sender_XiaoQing		0	\N	\N		USER	1	1	2025-12-11 20:39:46.007159	2025-12-11 20:39:48.309056
259	\N	d20c1da4@test.com	\N	Receiver_YouYou		0	\N	\N		USER	1	1	2025-12-11 20:39:46.418254	2025-12-11 20:39:48.32392
260	\N	dab19a2b@test.com	\N	Sender_XiaoQing		0	\N	\N		USER	1	1	2025-12-11 20:39:48.368052	2025-12-11 20:39:50.046835
261	\N	ae45fb7a@test.com	\N	Receiver_YouYou		0	\N	\N		USER	1	1	2025-12-11 20:39:48.373594	2025-12-11 20:39:50.050651
274	\N	garhlz257@gmail.com	$2a$12$8u61l74yyxBO/wKRUpohBea.wZCHLcNwOmUv9wylNDxNTvWd6k7Iu	admin	https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg	0	\N	\N		ADMIN	1	0	2025-12-12 08:46:01.867776	2025-12-12 08:46:01.867776
275	test_openid_1765532063695	admin1@test.com	$2a$10$CcO2oC3cPY7p0yTbKRkSKuEIdYfRfGOGyw2to1QP9TgjK9FDzAHvS	小青	https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg	0	\N	\N		ADMIN	1	0	2025-12-12 17:34:23.700905	2025-12-12 17:34:23.702853
276	\N	ai_bot@szu.edu.cn	random_password_cannot_login	AI省流助手	https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg	0	\N	\N		ADMIN	1	0	2025-12-12 19:52:43.905878	2025-12-12 19:52:43.905878
283	\N	photo@test.com	$2a$10$rT/xOkHVCfdPbi31IvJhkeJs457F.fUy4kmfXZFmelNQB4mLAOj0q	PhotoLife	https://api.dicebear.com/7.x/avataaars/svg?seed=PhotoLife	0	\N	\N	用镜头记录生活的美好	USER	1	0	2025-12-13 14:48:30.922389	2025-12-13 14:48:30.922441
284	\N	jane@test.com	$2a$10$lD5vuQXBUiPF2JniDNONfeS8wlRraFM92VwyQhNOlPDWfBl1uUcyO	FoodieJane	https://api.dicebear.com/7.x/avataaars/svg?seed=FoodieJane	0	\N	\N	探店达人 | 寻找城市角落的美味	USER	1	0	2025-12-13 14:48:31.021005	2025-12-13 14:48:31.021044
285	\N	mike@test.com	$2a$10$FM/4.Li8JNKbAVBKPifgF.s8kl7tLFlEGYof84mVnfTMLPs/MF9sK	StudentMike	https://api.dicebear.com/7.x/avataaars/svg?seed=StudentMike	0	\N	\N	大三学生，正在准备考研	USER	1	0	2025-12-13 14:48:31.122688	2025-12-13 14:48:31.122721
286	\N	cat@test.com	$2a$10$6Pbfc3qj6iuCLc.9iFog4.UyKxzVaJlUWAWnUQt6hlL53Qm6aoBTi	CatLover	https://api.dicebear.com/7.x/avataaars/svg?seed=CatLover	0	\N	\N	猫奴一枚，只有猫猫能治愈我	USER	1	0	2025-12-13 14:48:31.225087	2025-12-13 14:48:31.225124
17	test_openid_1764763381547	test5@test.com	$2a$10$iMN1NCwC2F8t3t3DyS6GOO.Hw3kIHlccMY7HPds98duOEtgsaDtLy	映记测试账号	https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg	0	\N	\N		USER	1	0	2025-12-03 20:03:01.553754	2025-12-03 20:03:01.555836
\.


--
-- Name: users_id_seq; Type: SEQUENCE SET; Schema: public; Owner: elaine
--

SELECT pg_catalog.setval('public.users_id_seq', 286, true);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: elaine
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_openid_key; Type: CONSTRAINT; Schema: public; Owner: elaine
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_openid_key UNIQUE (openid);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: elaine
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: idx_users_email; Type: INDEX; Schema: public; Owner: elaine
--

CREATE INDEX idx_users_email ON public.users USING btree (email);


--
-- Name: idx_users_openid; Type: INDEX; Schema: public; Owner: elaine
--

CREATE INDEX idx_users_openid ON public.users USING btree (openid);

--
-- Web-only user service migration (one service, one DB)
-- NOTE: This drops openid and enforces email/password login.
--

ALTER TABLE ONLY public.users
    DROP CONSTRAINT IF EXISTS users_openid_key;

DROP INDEX IF EXISTS idx_users_openid;

ALTER TABLE ONLY public.users
    DROP COLUMN IF EXISTS openid;

ALTER TABLE ONLY public.users
    ALTER COLUMN email SET NOT NULL,
    ALTER COLUMN password SET NOT NULL;

ALTER TABLE ONLY public.users
    ADD COLUMN token_version integer DEFAULT 0 NOT NULL,
    ADD COLUMN password_changed_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL;


--
-- PostgreSQL database dump complete
--
