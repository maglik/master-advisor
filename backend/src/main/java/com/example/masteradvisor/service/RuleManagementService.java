package com.example.masteradvisor.service;

import com.example.masteradvisor.dto.ApplicantDto;
import com.example.masteradvisor.dto.RecommendationDto;
import com.example.masteradvisor.entity.BusinessRule;
import com.example.masteradvisor.repository.RuleRepository;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class RuleManagementService {

    private final RuleRepository ruleRepository;
    private KieContainer kieContainer;

    public RuleManagementService(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @PostConstruct
    public void init() {
        log.info("=== НАЧАЛО ЗАГРУЗКИ ПРАВИЛ ===");
        loadRulesFromDatabase();
    }

    public void loadRulesFromDatabase() {
        log.info("Загрузка правил из базы данных...");

        List<BusinessRule> activeRules = ruleRepository.findByActiveTrue();
        log.info("Найдено активных правил: {}", activeRules.size());

        if (activeRules.isEmpty()) {
            log.warn("Нет активных правил в базе данных");
            return;
        }

        for (BusinessRule rule : activeRules) {
            log.info("Правило: id={}, name={}, active={}, drl длина={}",
                    rule.getId(), rule.getName(), rule.getActive(),
                    rule.getDrlContent() != null ? rule.getDrlContent().length() : 0);
        }

        try {
            KieServices ks = KieServices.Factory.get();
            KieFileSystem kfs = ks.newKieFileSystem();

            String kmoduleXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<kmodule xmlns=\"http://www.drools.org/xsd/kmodule\">\n" +
                    "  <kbase name=\"rulesBase\" packages=\"com.masteradvisor.rules\">\n" +
                    "    <ksession name=\"rulesSession\" default=\"true\"/>\n" +
                    "  </kbase>\n" +
                    "</kmodule>";
            kfs.writeKModuleXML(kmoduleXml);
            log.info("kmodule.xml добавлен");

            for (BusinessRule rule : activeRules) {
                String path = "src/main/resources/com/masteradvisor/rules/rule_" + rule.getId() + ".drl";
                kfs.write(path, rule.getDrlContent());
                log.info("Добавлено DRL: {}", path);
            }

            KieBuilder kieBuilder = ks.newKieBuilder(kfs);
            log.info("Начинаем компиляцию...");
            kieBuilder.buildAll();
            log.info("=== buildAll completed, checking results ===");

            if (kieBuilder.getResults().hasMessages(Message.Level.ERROR)) {
                log.error("=== ОШИБКИ КОМПИЛЯЦИИ ===");
                kieBuilder.getResults().getMessages().forEach(msg ->
                        log.error("Ошибка: {} (файл: {}, строка: {})",
                                msg.getText(), msg.getPath(), msg.getLine())
                );
                throw new RuntimeException("Ошибка компиляции правил");
            }

            log.info("Компиляция успешна!");
            this.kieContainer = ks.newKieContainer(ks.getRepository().getDefaultReleaseId());
            log.info("KieContainer создан");

        } catch (Exception e) {
            log.error("Исключение при загрузке правил: {}", e.getMessage(), e);
        }
    }
    public List<RecommendationDto> executeRules(ApplicantDto applicant) {
        if (kieContainer == null) {
            log.warn("KieContainer не инициализирован");
            return List.of();
        }

        KieSession session = kieContainer.newKieSession();
        List<RecommendationDto> recommendations = new ArrayList<>();

        // Устанавливаем глобальную переменную для сбора результатов
        session.setGlobal("recommendations", recommendations);

        // Добавляем факты
        session.insert(applicant);

        // Запускаем правила
        session.fireAllRules();
        session.dispose();

        return recommendations;
    }

    // Метод для перезагрузки правил (можно вызвать через админку)
    public void refreshRules() {
        log.info("Перезагрузка правил...");
        loadRulesFromDatabase();
    }
}