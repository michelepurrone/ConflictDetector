package org.conflictdetector.app;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Dictionary;
import java.util.Properties;
import static org.onlab.util.Tools.get;
import org.onosproject.core.*;
import org.onosproject.net.flow.*;
import org.onosproject.net.device.*;
import org.onosproject.net.*;
import java.util.*;

@Component(immediate = true,
        service = {SomeInterface.class},
        property = {
                "someProperty=Some Default String Value",
        })
public class AppComponent implements SomeInterface {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Some configurable property. */
    private String someProperty;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Activate
    public void activate() {
        ApplicationId appId = coreService.registerApplication("Conflict Resolver");
        flowRuleService.removeFlowRulesById(appId);

        new Thread(() -> {
            Iterable<Device> startDevices = deviceService.getDevices();
            Iterable<FlowEntry> startRules = null;
            for (Device d : startDevices) {
                startRules = flowRuleService.getFlowEntries(d.id());

            }
            List<MyRule> startFlowList = new ArrayList<>();
            for (FlowEntry f : startRules) {
                startFlowList.add(new MyRule(f.id().value(), f, false));
            }

            //Setup
            List<MyRule> conflictList = new ArrayList<MyRule>();
            List<MyRule> flowList = new ArrayList<MyRule>();
            HashMap<String, MyScore> users = new HashMap<>();
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            Set<String> bannedIp = new HashSet<>();
            while(true) {
                try {
                    for(MyRule f : conflictList) {
                        if(f.isRemovable()) {
                            flowList.remove(f);
                        }
                    }
                    conflictList.clear();
                    Iterable<Device> devices = deviceService.getDevices();
                    Iterable<FlowEntry> rules = null;
                    for (Device d : devices) {
                        rules = flowRuleService.getFlowEntries(d.id());

                    }
                    for (FlowEntry f : rules) {
                        if(!flowList.contains(new MyRule(f.id().value(), f, false))){
                            flowList.add(new MyRule(f.id().value(), f, false));
                        }

                    }
                    flowList.removeAll(startFlowList);
                    log.info("Numero di regole candidate: {}", flowList.size());
                    try {
                        Thread.sleep(4000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    myLoop: for (MyRule f : flowList) {

                        

                        //Il campo metadati contiene l'indirizzo IP di chi carica la regola.
                        String ip = longToIp(f.getFlow().selector().getCriterion(Criterion.Type.METADATA).toString());
                        MyScore score = users.getOrDefault(ip, new MyScore());
                        users.put(ip, score);

                        //Se l'IP è stato bannato, non carico la regola
                        if (bannedIp.contains(ip)) {
                            flowRuleService.removeFlowRules(f.getFlow());
                            conflictList.add(f);
                            f.setRemovable(true);
                            break;
                        }

                        double punishment = users.get(ip).getPunishment();
                        
                        
                        //Controllo delle regole
                        if (!overlap(startFlowList, f).isEmpty() && !f.isRemovable()) {
                            punishment += 0.05;
                            flowRuleService.removeFlowRules(f.getFlow());
                            users.get(ip).startTimer();
                            conflictList.add(f);
                        }
                        if (!redundancy(startFlowList, f).isEmpty() && !f.isRemovable()) {
                            punishment += 0.1;
                            flowRuleService.removeFlowRules(f.getFlow());
                            users.get(ip).startTimer();
                            conflictList.add(f);
                        }
                        if (!shadowing(startFlowList, f).isEmpty() && !f.isRemovable()) {
                            punishment += 0.5;
                            flowRuleService.removeFlowRules(f.getFlow());
                            users.get(ip).startTimer();
                            conflictList.add(f);
                        }
                        if (!generalization(startFlowList, f).isEmpty() && !f.isRemovable()) {
                            punishment += 0.2;
                            flowRuleService.removeFlowRules(f.getFlow());
                            users.get(ip).startTimer();
                            conflictList.add(f);
                        }
                        if (!correlation(startFlowList, f).isEmpty() && !f.isRemovable()) {
                            punishment += 0.3;
                            flowRuleService.removeFlowRules(f.getFlow());
                            users.get(ip).startTimer();
                            conflictList.add(f);
                        }
                        //Se non ci sono conflitti, carico la regola
                        if (!conflictList.contains(f)) {
                            startFlowList.add(f);
                            f.setRemovable(true);
                            conflictList.add(f);
                            flowRuleService.applyFlowRules(f.getFlow());
                            users.get(ip).setLoadedRules(users.get(ip).getLoadedRules() + 1);
                            log.info("L'utente con IP: {} " + ip + " ha {} regole caricate", users.get(ip).getLoadedRules());
                            if (users.get(ip).getAttempts() != 0) {
                                users.get(ip).startTimer();
                            }
                            users.get(ip).setAttempts(0);
                            punishment = 0.0;
                        }

                        //MECCANISMO DI PUNISHMENT E REWARD
                        
                        users.get(ip).setPunishment(punishment);
                        if(punishment < 0.0) {
                            users.get(ip).setPunishment(0.0);
                        }
                        else if(punishment > 1.0) {
                            users.get(ip).setPunishment(1.0);
                        }

                        double oldTrustness = users.get(ip).getTrustness();
                        double newTrustness;

                        double oldAlpha = users.get(ip).getAlpha();
                        double newAlpha;

                        if(oldTrustness < 0.0) {
                            oldTrustness = 0.0;
                        }else if(oldTrustness > 1.0) {
                            oldTrustness = 1.0;
                        }


                        //Calcolo del reward
                        double beta = 0.001;
                        newAlpha = oldAlpha * (1 + beta * oldTrustness);
                        double reward = newAlpha * (users.get(ip).getTime()) + 0.001;
                        if(reward > 1.0) {
                            reward = 1.0;
                        }
                        else if(reward < 0.0) {
                            reward = 0.0;
                        }

                        //Calcolo della nuova trustness
                        double lambda = 0.7;
                        // newTrustness = lambda*oldTrustness + (1-lambda)*(reward - punishment);
                        newTrustness = oldTrustness + (reward - punishment);

                        if(newTrustness < 0.0) {
                            newTrustness = 0.0;
                        }else if(newTrustness > 1.0) {
                            newTrustness = 1.0;
                        }

                        users.get(ip).setTrustness(newTrustness);
                        users.get(ip).setPunishment(0.0);
                        users.get(ip).setAlpha(newAlpha);

                     


                        //Meccanismo di blocco dell'IP sospetto
                        for (Map.Entry<String, MyScore> pair : users.entrySet()) {
                            //Se l'utente ha una trustness minore di 0.7, viene considerato un attaccante
                            if (pair.getKey().equals(ip) && pair.getValue().getTrustness() < 0.7) {
                                pair.getValue().setAttempts(pair.getValue().getAttempts() + 1);
                                if (pair.getValue().getAttempts() == 1) {
                                    flowRuleService.removeFlowRules(f.getFlow());
                                    users.get(ip).setBannedRules(users.get(ip).getBannedRules() + 1);
                                    conflictList.add(f);
                                    f.setRemovable(true);
                                    bannedIp.add(pair.getKey());
                                    //scheduler per rimuovere l'IP dalla lista dei bannati
                                    scheduler.schedule(() -> {
                                        bannedIp.remove(pair.getKey());
                                    }, 2, TimeUnit.SECONDS);
                                    break myLoop;
                                }
                                if (pair.getValue().getAttempts() == 2) {
                                   //se l'utente viene riconosciuto come attaccante per la seconda volta, viene bannato per 4 secondi
                                    flowRuleService.removeFlowRules(f.getFlow());
                                    users.get(ip).setBannedRules(users.get(ip).getBannedRules() + 1);
                                    conflictList.add(f);
                                    f.setRemovable(true);
                                    bannedIp.add(pair.getKey());

                                    scheduler.schedule(() -> {
                                        bannedIp.remove(pair.getKey());
                                    }, 4, TimeUnit.SECONDS);
                                    break myLoop;
                                }
                                if (pair.getValue().getAttempts() == 3) {
                                    //se l'utente viene riconosciuto come attaccante per la terza volta, viene bannato per 8 secondi
                                    flowRuleService.removeFlowRules(f.getFlow());
                                    users.get(ip).setBannedRules(users.get(ip).getBannedRules() + 1);
                                    conflictList.add(f);
                                    f.setRemovable(true);
                                    bannedIp.add(pair.getKey());

                                    scheduler.schedule(() -> {
                                        bannedIp.remove(pair.getKey());
                                    }, 8, TimeUnit.SECONDS);
                                    break myLoop;
                                }
                                if (pair.getValue().getAttempts() >= 4) {
                                   //se l'utente viene riconosciuto come attaccante per la quarta volta, viene bannato per sempre
                                    flowRuleService.removeFlowRules(f.getFlow());
                                    users.get(ip).setBannedRules(users.get(ip).getBannedRules() + 1);
                                    log.info("L'utente con IP: {} " + ip + " ha " + users.get(ip).getBannedRules() + " regole bannate. Non potrà più caricare regole.");
                                    conflictList.add(f);
                                    f.setRemovable(true);
                                    bannedIp.add(pair.getKey());
                                    break myLoop;
                                }
                            }
                        }
                    }

                    Thread.sleep(1000);
                } catch (InterruptedException | UnknownHostException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public String longToIp(String ipString) {
        long ip = Long.parseLong(ipString.split(":")[1], 16);
        return ((ip >> 24) & 0xFF) + "."
                + ((ip >> 16) & 0xFF) + "."
                + ((ip >> 8) & 0xFF) + "."
                + (ip & 0xFF);
    }

    public static boolean subnet(String ip1WithMask, String ip2WithMask) throws UnknownHostException {
        String[] partsIP = ip1WithMask.split(":");
        String[] partsSubnet = ip2WithMask.split(":");
        String ipAddressPart = partsIP[1];
        String subnetAddressPart = partsSubnet[1];
        String[] ip1Parts = ipAddressPart.split("/");
        String[] ip2Parts = subnetAddressPart.split("/");
        InetAddress ip1Address = InetAddress.getByName(ip1Parts[0]);
        InetAddress ip2Address = InetAddress.getByName(ip2Parts[0]);
        int ip1PrefixLength = Integer.parseInt(ip1Parts[1]);
        int ip2PrefixLength = Integer.parseInt(ip2Parts[1]);
        byte[] ip1Bytes = ip1Address.getAddress();
        byte[] ip2Bytes = ip2Address.getAddress();
        // Applichiamo la maschera a entrambi gli indirizzi
        byte[] ip1Network = applyMask(ip1Bytes, ip1PrefixLength);
        byte[] ip2Network = applyMask(ip2Bytes, ip2PrefixLength);
        // Controllo per subset
        if (ip1PrefixLength >= ip2PrefixLength) {
            byte[] ip1SubsetCheck = applyMask(ip1Bytes, ip2PrefixLength);
            return Arrays.equals(ip1SubsetCheck, ip2Network);
        }
        return false;
    }

    private static byte[] applyMask(byte[] address, int prefixLength) {
        byte[] maskedAddress = new byte[address.length];
        int fullBytes = prefixLength / 8;
        int remainderBits = prefixLength % 8;
        System.arraycopy(address, 0, maskedAddress, 0, fullBytes);
        if (fullBytes < address.length) {
            maskedAddress[fullBytes] = (byte) (address[fullBytes] & (0xFF << (8 - remainderBits)));
        }
        return maskedAddress;
    }

    public List<MyRule> redundancy(List<MyRule> flows, MyRule flow) throws UnknownHostException {
        List<MyRule> flowList = new ArrayList<MyRule>();
        for(MyRule f : flows) {
            if(f.getFlow().selector().getCriterion(Criterion.Type.IP_PROTO)!=null) {
                Instruction insF = f.getFlow().treatment().allInstructions().stream().filter(i -> (i.type() == Instruction.Type.OUTPUT)).findFirst().orElse(null);
                Instruction insFlow = flow.getFlow().treatment().allInstructions().stream().filter(i -> (i.type() == Instruction.Type.OUTPUT)).findFirst().orElse(null);
                if (
                        Objects.equals(insF,insFlow) && f.getFlow().selector().getCriterion(Criterion.Type.IP_PROTO).equals(flow.getFlow().selector().getCriterion(Criterion.Type.IP_PROTO)) &&
                                ((flow.getFlow().treatment().allInstructions().isEmpty() && f.getFlow().treatment().allInstructions().isEmpty()) ||
                                        (!flow.getFlow().treatment().allInstructions().isEmpty() && !f.getFlow().treatment().allInstructions().isEmpty())) &&
                                (subnet(flow.getFlow().selector().getCriterion(Criterion.Type.IPV4_SRC).toString(), f.getFlow().selector().getCriterion(Criterion.Type.IPV4_SRC).toString()) &&
                                        subnet(flow.getFlow().selector().getCriterion(Criterion.Type.IPV4_DST).toString(), f.getFlow().selector().getCriterion(Criterion.Type.IPV4_DST).toString()))) {
                    flowList.add(f);
                    //log.info("La regola con priorità: {} " + flow.getFlow().priority() + " è ridondante con la regola con priorità: {}" + f.getFlow().priority());
                }
            }
        }
        return flowList;
    }

    public List<MyRule> shadowing(List<MyRule> flows, MyRule flow) throws UnknownHostException {
        List<MyRule> flowList = new ArrayList<MyRule>();
        for (MyRule f : flows) {
            if (f.getFlow().selector().getCriterion(Criterion.Type.IP_PROTO) != null) {
                Instruction insF = f.getFlow().treatment().allInstructions().stream().filter(i -> (i.type() == Instruction.Type.OUTPUT)).findFirst().orElse(null);
                Instruction insFlow = flow.getFlow().treatment().allInstructions().stream().filter(i -> (i.type() == Instruction.Type.OUTPUT)).findFirst().orElse(null);
                if(insFlow == null && insF==null) {
                    return flowList;
                }
                if (f.getFlow().priority() < flow.getFlow().priority() && !Objects.equals(insF, insFlow) && f.getFlow().selector().getCriterion(Criterion.Type.IP_PROTO).equals(flow.getFlow().selector().getCriterion(Criterion.Type.IP_PROTO)) &&
                        (subnet(f.getFlow().selector().getCriterion(Criterion.Type.IPV4_SRC).toString(), flow.getFlow().selector().getCriterion(Criterion.Type.IPV4_SRC).toString()) ||
                                subnet(f.getFlow().selector().getCriterion(Criterion.Type.IPV4_DST).toString(), flow.getFlow().selector().getCriterion(Criterion.Type.IPV4_DST).toString()))) {
                    flowList.add(f);
                    //log.info("La regola con priorità: {} " + flow.getFlow().priority() + " ombreggia la regola con priorità: {}" + f.getFlow().priority());
                }
            }

        }
        return flowList;
    }

    public List<MyRule> generalization(List<MyRule> flows, MyRule flow) throws UnknownHostException {
        List<MyRule> flowList = new ArrayList<MyRule>();
        for (MyRule f : flows) {
            if (f.getFlow().selector().getCriterion(Criterion.Type.IP_PROTO) != null) {
                Instruction insF = f.getFlow().treatment().allInstructions().stream().filter(i -> (i.type() == Instruction.Type.OUTPUT)).findFirst().orElse(null);
                Instruction insFlow = flow.getFlow().treatment().allInstructions().stream().filter(i -> (i.type() == Instruction.Type.OUTPUT)).findFirst().orElse(null);
                if(insFlow == null && insF==null) {
                    return flowList;
                }
                if (f.getFlow().priority() > flow.getFlow().priority() && !Objects.equals(insF, insFlow) && f.getFlow().selector().getCriterion(Criterion.Type.IP_PROTO).equals(flow.getFlow().selector().getCriterion(Criterion.Type.IP_PROTO)) &&
                        subnet(f.getFlow().selector().getCriterion(Criterion.Type.IPV4_SRC).toString(), flow.getFlow().selector().getCriterion(Criterion.Type.IPV4_SRC).toString()) &&
                        subnet(f.getFlow().selector().getCriterion(Criterion.Type.IPV4_DST).toString(), flow.getFlow().selector().getCriterion(Criterion.Type.IPV4_DST).toString())) {
                    flowList.add(f);
                    //log.info("La regola con priorità: {} " + flow.getFlow().priority() + " generalizza la regola con priorità: {}" + f.getFlow().priority());
                }
            }

        }
        return flowList;
    }

    public List<MyRule> correlation(List<MyRule> flows, MyRule flow) throws UnknownHostException {
        List<MyRule> flowList = new ArrayList<MyRule>();
        for (MyRule f : flows) {
            if (f.getFlow().selector().getCriterion(Criterion.Type.IP_PROTO) != null && f.getFlow().selector().getCriterion(Criterion.Type.IPV4_SRC) != null && f.getFlow().selector().getCriterion(Criterion.Type.IPV4_DST) != null) {
                Instruction insF = f.getFlow().treatment().allInstructions().stream().filter(i -> (i.type() == Instruction.Type.OUTPUT)).findFirst().orElse(null);
                Instruction insFlow = flow.getFlow().treatment().allInstructions().stream().filter(i -> (i.type() == Instruction.Type.OUTPUT)).findFirst().orElse(null);

                if (f.getFlow().priority() == flow.getFlow().priority() &&
                        !Objects.equals(insF, insFlow) && f.getFlow().selector().getCriterion(Criterion.Type.IP_PROTO).equals(flow.getFlow().selector().getCriterion(Criterion.Type.IP_PROTO))  &&
                        (subnet(f.getFlow().selector().getCriterion(Criterion.Type.IPV4_SRC).toString(), flow.getFlow().selector().getCriterion(Criterion.Type.IPV4_SRC).toString()) ||
                                subnet(f.getFlow().selector().getCriterion(Criterion.Type.IPV4_DST).toString(), flow.getFlow().selector().getCriterion(Criterion.Type.IPV4_DST).toString()))) {
                    flowList.add(f);
                    //log.info("La regola con priorità: {} " + flow.getFlow().priority() + " è correlata alla regola con priorità: {} " + f.getFlow().priority());
                }
            }
        }
        return flowList;
    }

    public List<MyRule> overlap(List<MyRule> flows, MyRule flow) throws UnknownHostException {
        List<MyRule> flowList = new ArrayList<MyRule>();
        for (MyRule f : flows) {
            if (f.getFlow().selector().getCriterion(Criterion.Type.IP_PROTO) != null && f.getFlow().selector().getCriterion(Criterion.Type.IPV4_SRC) != null && f.getFlow().selector().getCriterion(Criterion.Type.IPV4_DST) != null) {
                Instruction insF = f.getFlow().treatment().allInstructions().stream().filter(i -> (i.type() == Instruction.Type.OUTPUT)).findFirst().orElse(null);
                Instruction insFlow = flow.getFlow().treatment().allInstructions().stream().filter(i -> (i.type() == Instruction.Type.OUTPUT)).findFirst().orElse(null);
                if((insFlow == null && insF!=null) || (insFlow != null && insF == null)) {
                    return flowList;
                }

                if (((flow.getFlow().treatment().allInstructions().isEmpty() && f.getFlow().treatment().allInstructions().isEmpty()) ||
                        (!flow.getFlow().treatment().allInstructions().isEmpty() && !f.getFlow().treatment().allInstructions().isEmpty())) &&
                        (subnet(f.getFlow().selector().getCriterion(Criterion.Type.IPV4_SRC).toString(), flow.getFlow().selector().getCriterion(Criterion.Type.IPV4_SRC).toString()) ||
                                subnet(f.getFlow().selector().getCriterion(Criterion.Type.IPV4_DST).toString(), flow.getFlow().selector().getCriterion(Criterion.Type.IPV4_DST).toString())) &&
                        Objects.equals(insF, insFlow) && f.getFlow().selector().getCriterion(Criterion.Type.IP_PROTO).equals(flow.getFlow().selector().getCriterion(Criterion.Type.IP_PROTO))
                ) {
                    flowList.add(f);
                    //log.info("La regola con priorità: {} " + flow.getFlow().priority() + " è sovrapposta alla regola con priorità: {}" + f.getFlow().priority());
                }
            }
        }
        return flowList;
    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);
        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        if (context != null) {
            someProperty = get(properties, "someProperty");
        }
        log.info("Reconfigured");
    }

    @Override
    public void someMethod() {
        log.info("Invoked");
    }

}

