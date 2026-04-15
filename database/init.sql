-- Создание таблиц для хранения правил и данных

-- Таблица для хранения DRL-правил
CREATE TABLE IF NOT EXISTS business_rule (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    drl_content TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Таблица для программ магистратуры
CREATE TABLE IF NOT EXISTS program (
    code VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    pass_score_prev_year INTEGER,
    pass_score_current INTEGER,
    budget_places INTEGER,
    paid_places INTEGER,
    active BOOLEAN DEFAULT TRUE
);

-- Таблица для вопросов анкеты
CREATE TABLE IF NOT EXISTS question (
    id SERIAL PRIMARY KEY,
    text TEXT NOT NULL,
    type VARCHAR(20) NOT NULL,  -- SINGLE, MULTIPLE
    order_index INTEGER NOT NULL,
    active BOOLEAN DEFAULT TRUE
);

-- Таблица для вариантов ответов
CREATE TABLE IF NOT EXISTS answer_option (
    id SERIAL PRIMARY KEY,
    question_id INTEGER REFERENCES question(id) ON DELETE CASCADE,
    text VARCHAR(255) NOT NULL,
    value VARCHAR(100) NOT NULL
);

-- Вставка тестового правила
INSERT INTO business_rule (name, drl_content, version, active) 
VALUES ('high_score_recommendation', 
'package com.masteradvisor.rules

import com.example.masteradvisor.dto.Applicant
import com.example.masteradvisor.dto.Recommendation

rule "High Score Recommendation"
    when
        $a: Applicant(score > 70)
    then
        System.out.println("High score detected: " + $a.getScore());
        insert(new Recommendation("AI Engineering", "budget", 0.9));
end', 1, true) ON CONFLICT DO NOTHING;

-- Вставка тестовой программы
INSERT INTO program (code, name, pass_score_prev_year, active) 
VALUES ('AI_ENG', 'AI Engineering', 70, true) ON CONFLICT DO NOTHING;