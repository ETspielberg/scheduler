package unidue.ub.scheduler;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.client.Traverson;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
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

    @Scheduled(cron="0 0 1 * * 6")
    public void updateNotations() throws IOException {
        callRestService("http://localhost:8082/services/run/notationbuilder");
    }

    @Scheduled(cron="0 0 2 * * *")
    public void updateNrequests() throws IOException {
        callBatchJob("nrequests","");
    }

    @Scheduled(cron="0 0 23 * * SAT")
    public void runEventanalyzer() throws IOException, URISyntaxException {
        List<Stockcontrol> stockcontrols = (List<Stockcontrol>) getAllActiveStockcontrol(HalfAYear);
        for (Stockcontrol stockcontrol : stockcontrols) {
            callBatchJob("eventanalyzer",stockcontrol.getIdentifier());
        }
    }

    private List<? extends Profile> getAllActiveStockcontrol(long interval) throws URISyntaxException {
        Traverson traverson = new Traverson(new URI("http://localhost:8082/api/settings/stockcontrol"), MediaTypes.HAL_JSON);
        Traverson.TraversalBuilder tb = traverson.follow("$._links.self.href");
        ParameterizedTypeReference<Resources<Stockcontrol>> typeRefDevices = new ParameterizedTypeReference<Resources<Stockcontrol>>() {};
        Resources<Stockcontrol> resUsers = tb.toObject(typeRefDevices);
        Collection<Stockcontrol> foundNotations = resUsers.getContent();
        List<Stockcontrol> toBeExecuted = new ArrayList<>();
        for (Stockcontrol stockcontrol : foundNotations) {
            if (stockcontrol.getLastrun().getTime() < System.currentTimeMillis()- interval) {
                toBeExecuted.add(stockcontrol);
            }
        }
        return toBeExecuted;
    }

    @Scheduled(cron="0 0 1 15 * ?")
    public void collectSushi() throws URISyntaxException, IOException {
        Traverson traverson = new Traverson(new URI("http://localhost:8082/api/settings/sushiprovider"), MediaTypes.HAL_JSON);
        Traverson.TraversalBuilder tb = traverson.follow("$._links.self.href");
        ParameterizedTypeReference<Resources<Sushiprovider>> typeRefDevices = new ParameterizedTypeReference<Resources<Sushiprovider>>() {};
        Resources<Sushiprovider> resUsers = tb.toObject(typeRefDevices);
        Collection<Sushiprovider> sushiproviders = resUsers.getContent();
        for (Sushiprovider sushiprovider : sushiproviders) {
            callBatchJob("sushi",sushiprovider.getIdentifier());
        }
    }

    private int callBatchJob(String service, String identifier) throws IOException {
        HttpClient client = new HttpClient();
        GetMethod get = new GetMethod("http://localhost:8082/services/batch/run/" + service + "?identifier=" + identifier);
        return client.executeMethod(get);
    }
    
    private int callRestService(String url) throws IOException {
        HttpClient client = new HttpClient();
        GetMethod get = new GetMethod(url);
        return client.executeMethod(get);
    }


}
