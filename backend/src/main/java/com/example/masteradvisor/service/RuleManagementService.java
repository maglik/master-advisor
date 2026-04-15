package com.example.masteradvisor.service;

import com.example.masteradvisor.entity.BusinessRule;
import com.example.masteradvisor.repository.RuleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.kie.api.builder.KieModule;


@Service
public class RuleManagementService {

    private static final Logger log = LoggerFactory.getLogger(RuleManagementService.class);

    private final RuleRepository ruleRepository;
    private final WebClient.Builder webClientBuilder;

    @Value("${kie.server.url}")
    private String kieServerUrl;

    @Value("${kie.server.username}")
    private String username;

    @Value("${kie.server.password}")
    private String password;

    @Value("${kie.server.container-id}")
    private String containerId;

    private WebClient webClient;

    public RuleManagementService(RuleRepository ruleRepository, WebClient.Builder webClientBuilder) {
        this.ruleRepository = ruleRepository;
        this.webClientBuilder = webClientBuilder;
    }

    @PostConstruct
    public void init() {
        String auth = username + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        this.webClient = webClientBuilder
                .baseUrl(kieServerUrl)
                .defaultHeader("Authorization", "Basic " + encodedAuth)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();

        // При старте загружаем правила
        //loadAndDeployRules();
    }

    public void loadAndDeployRules() {
        log.info("Загружаем правила из базы данных...");
        List<BusinessRule> activeRules = ruleRepository.findByActiveTrue();

        if (activeRules.isEmpty()) {
            log.warn("Нет активных правил");
            return;
        }

        try {
            byte[] kjarContent = buildKjar(activeRules);
            deployToKieServer(kjarContent);
            log.info("Правила успешно задеплоены. Количество: {}", activeRules.size());
        } catch (Exception e) {
            log.error("Ошибка деплоя правил: {}", e.getMessage(), e);
        }
    }

    private byte[] buildKjar(List<BusinessRule> rules) throws IOException {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();

        // Временная папка для сборки KJAR
        Path tempDir = Files.createTempDirectory("kjar_");

        String version = "1.0." + System.currentTimeMillis();
        ReleaseId releaseId = ks.newReleaseId("com.masteradvisor", "dynamic-rules", version);
        kfs.generateAndWritePomXML(releaseId);

        // kmodule.xml
        String kmoduleXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<kmodule xmlns=\"http://www.drools.org/xsd/kmodule\">\n" +
                "  <kbase name=\"rulesBase\" packages=\"com.masteradvisor.rules\">\n" +
                "    <ksession name=\"rulesSession\" default=\"true\"/>\n" +
                "  </kbase>\n" +
                "</kmodule>";
        kfs.writeKModuleXML(kmoduleXml);

        for (BusinessRule rule : rules) {
            String path = "src/main/resources/com/masteradvisor/rules/rule_" + rule.getId() + ".drl";
            kfs.write(path, rule.getDrlContent());
            log.debug("Добавлено правило: {}", rule.getName());
        }

        // Строим
        KieBuilder kieBuilder = ks.newKieBuilder(kfs).buildAll();
        if (kieBuilder.getResults().hasMessages(Message.Level.ERROR)) {
            kieBuilder.getResults().getMessages().forEach(msg ->
                    log.error("Ошибка компиляции: {}", msg.getText())
            );
            throw new RuntimeException("Ошибка компиляции правил");
        }

        // Получаем KJAR как массив байт через временный файл
        KieModule kieModule = kieBuilder.getKieModule();
        ReleaseId relId = kieModule.getReleaseId();

        // Путь к KJAR в локльном Maven-репозитории
        String m2RepoPath = System.getProperty("user.home") + "/.m2/repository/"
                + relId.getGroupId().replace('.', '/') + "/"
                + relId.getArtifactId() + "/"
                + relId.getVersion() + "/"
                + relId.getArtifactId() + "-" + relId.getVersion() + ".jar";

        Path jarPath = Paths.get(m2RepoPath);
        if (!Files.exists(jarPath)) {
            throw new RuntimeException("KJAR not found at: " + m2RepoPath);
        }

        return Files.readAllBytes(jarPath);
    }

    private void deployToKieServer(byte[] kjarContent) throws Exception {
        // Сначала проверим, существует ли уже контейнер
        checkAndRemoveContainer();

        // Создаем временный файл с KJAR
        java.nio.file.Path tempKjar = java.nio.file.Files.createTempFile("kjar_", ".jar");
        java.nio.file.Files.write(tempKjar, kjarContent);

        // Отправляем PUT запрос на создание контейнера
        // KIE Server умеет принимать KJAR как binary через REST API
        String deployUrl = "/containers/" + containerId;

        Map<String, Object> requestBody = Map.of(
                "release-id", Map.of(
                        "group-id", "com.masteradvisor",
                        "artifact-id", "dynamic-rules",
                        "version", "1.0." + System.currentTimeMillis()
                )
        );

        try {
            String response = webClient.put()
                    .uri(deployUrl)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class).flatMap(errorBody -> {
                                log.error("Ошибка KIE Server: {}", errorBody);
                                return Mono.error(new RuntimeException("KIE Server error: " + errorBody));
                            }))
                    .bodyToMono(String.class)
                    .block();

            log.info("Контейнер создан: {}", response);
        } catch (Exception e) {
            log.error("Не удалось создать контейнер: {}", e.getMessage());
            throw e;
        } finally {
            java.nio.file.Files.deleteIfExists(tempKjar);
        }
    }

    private void checkAndRemoveContainer() {
        String checkUrl = "/containers/" + containerId;

        try {
            // Пытаемся удалить контейнер, если он существует
            webClient.delete()
                    .uri(checkUrl)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("Существующий контейнер удален: {}", containerId);
        } catch (Exception e) {
            // Контейнера не было — это нормально
            log.debug("Контейнер не существовал: {}", containerId);
        }
    }

    public void refreshRules() {
        log.info("Обновление правил по запросу");
        loadAndDeployRules();
    }
}