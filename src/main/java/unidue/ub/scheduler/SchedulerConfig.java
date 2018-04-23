package unidue.ub.scheduler;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.client.Traverson;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;
import unidue.ub.settings.fachref.Profile;
import unidue.ub.settings.fachref.Stockcontrol;
import unidue.ub.settings.fachref.Sushiprovider;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Configuration
@EnableScheduling
public class SchedulerConfig {

    private final static long dayInMillis = 24L*60L*60L*1000L;

    private final static long HalfAYear = 182L * dayInMillis;

    private final Logger log = LoggerFactory.getLogger(SchedulerConfig.class);

    @Scheduled(cron="0 0 1 * * 6")
    public void updateNotations() {
        try {
            int response = callRestService("http://localhost:11844/run/notationbuilder");
            log.info("notationbuilder returned " + response);
        } catch (IOException e) {
            log.warn("could not run notation update");
            e.printStackTrace();
        }

    }

    @Scheduled(cron="0 0 2 * * *")
    public void updateNrequests() {
        try {
        int response = callBatchJob("nrequests","");
        log.info("Nrequests batch job returned " + response);
        } catch (IOException e) {
            log.warn("could not run nrequests collector");
            e.printStackTrace();
        }
    }

    @Scheduled(cron="0 0 23 * * SAT")
    public void runEventanalyzer() {
        List<Stockcontrol> stockcontrols = new ArrayList<>();
        try {
            stockcontrols = (List<Stockcontrol>) getAllActiveStockcontrol(HalfAYear);
            for (Stockcontrol stockcontrol : stockcontrols) {
                int response = callBatchJob("eventanalyzer", stockcontrol.getIdentifier());
                log.info("eventanalyzer job returned " + response);
            }
        } catch (Exception e) {
            log.warn("could not run eventanalyzer");
            e.printStackTrace();
        }
    }

    private List<? extends Profile> getAllActiveStockcontrol(long interval) throws URISyntaxException {
        Stockcontrol[] stockcontrols = new RestTemplate().getForEntity(
                "http://localhost:8082/api/settings/stockcontrol/all",
                Stockcontrol[].class
        ).getBody();
        List<Stockcontrol> toBeExecuted = new ArrayList<>();
        for (Stockcontrol stockcontrol : stockcontrols) {
            if (stockcontrol.getLastrun().getTime() < System.currentTimeMillis()- interval) {
                toBeExecuted.add(stockcontrol);
            }
        }
        return toBeExecuted;
    }

    @Scheduled(cron="0 0 1 24 * ?")
    public void collectSushi() {
        try {
            Sushiprovider[] sushiproviders = new RestTemplate().getForEntity(
                    "http://localhost:8082/api/settings/sushiprovider/all",
                    Sushiprovider[].class
            ).getBody();
            for (Sushiprovider sushiprovider : sushiproviders) {
                int response = callBatchJob("sushi", sushiprovider.getIdentifier());
                log.info("sushi job returned " + response);
            }
        } catch (Exception e) {
            log.warn("could not run SUSHI collector");
            e.printStackTrace();
        }
    }

    private int callBatchJob(String service, String identifier) throws IOException {
        HttpClient client = new HttpClient();
        GetMethod get = new GetMethod("http://localhost:11822/batch/" + service + "?identifier=" + identifier);
        return client.executeMethod(get);
    }
    
    private int callRestService(String url) throws IOException {

        HttpClient client = new HttpClient();
        GetMethod get = new GetMethod(url);
        return client.executeMethod(get);
    }


}
