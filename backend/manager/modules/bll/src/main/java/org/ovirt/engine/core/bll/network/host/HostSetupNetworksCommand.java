package org.ovirt.engine.core.bll.network.host;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.ovirt.engine.core.bll.NonTransactiveCommandAttribute;
import org.ovirt.engine.core.bll.ValidationResult;
import org.ovirt.engine.core.bll.VdsCommand;
import org.ovirt.engine.core.bll.VdsHandler;
import org.ovirt.engine.core.bll.context.CommandContext;
import org.ovirt.engine.core.bll.network.cluster.ManagementNetworkUtil;
import org.ovirt.engine.core.bll.network.cluster.NetworkClusterHelper;
import org.ovirt.engine.core.common.FeatureSupported;
import org.ovirt.engine.core.common.action.HostSetupNetworksParameters;
import org.ovirt.engine.core.common.businessentities.BusinessEntityMap;
import org.ovirt.engine.core.common.businessentities.Entities;
import org.ovirt.engine.core.common.businessentities.VDS;
import org.ovirt.engine.core.common.businessentities.network.Bond;
import org.ovirt.engine.core.common.businessentities.network.HostNetworkQos;
import org.ovirt.engine.core.common.businessentities.network.IpConfiguration;
import org.ovirt.engine.core.common.businessentities.network.Network;
import org.ovirt.engine.core.common.businessentities.network.NetworkAttachment;
import org.ovirt.engine.core.common.businessentities.network.NetworkBootProtocol;
import org.ovirt.engine.core.common.businessentities.network.VdsNetworkInterface;
import org.ovirt.engine.core.common.config.Config;
import org.ovirt.engine.core.common.config.ConfigValues;
import org.ovirt.engine.core.common.errors.EngineError;
import org.ovirt.engine.core.common.errors.EngineException;
import org.ovirt.engine.core.common.errors.EngineMessage;
import org.ovirt.engine.core.common.interfaces.FutureVDSCall;
import org.ovirt.engine.core.common.vdscommands.FutureVDSCommandType;
import org.ovirt.engine.core.common.vdscommands.HostNetwork;
import org.ovirt.engine.core.common.vdscommands.HostSetupNetworksVdsCommandParameters;
import org.ovirt.engine.core.common.vdscommands.UserConfiguredNetworkData;
import org.ovirt.engine.core.common.vdscommands.VDSCommandType;
import org.ovirt.engine.core.common.vdscommands.VDSReturnValue;
import org.ovirt.engine.core.common.vdscommands.VdsIdAndVdsVDSCommandParametersBase;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.core.compat.Version;
import org.ovirt.engine.core.dao.VdsDao;
import org.ovirt.engine.core.dao.network.HostNetworkQosDao;
import org.ovirt.engine.core.dao.network.NetworkAttachmentDao;
import org.ovirt.engine.core.dao.network.NetworkClusterDao;
import org.ovirt.engine.core.dao.network.NetworkDao;
import org.ovirt.engine.core.utils.NetworkUtils;
import org.ovirt.engine.core.utils.lock.EngineLock;
import org.ovirt.engine.core.utils.transaction.TransactionMethod;
import org.ovirt.engine.core.utils.transaction.TransactionSupport;
import org.ovirt.engine.core.vdsbroker.vdsbroker.HostNetworkTopologyPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonTransactiveCommandAttribute
public class HostSetupNetworksCommand<T extends HostSetupNetworksParameters> extends VdsCommand<T> {


    private static final Logger log = LoggerFactory.getLogger(HostSetupNetworksCommand.class);
    private BusinessEntityMap<Network> networkBusinessEntityMap;

    private Set<String> removedNetworks;
    private Set<String> removedBondNames;
    private List<VdsNetworkInterface> removedBonds;
    private Set<String> removedUnmanagedNetworks;
    private List<VdsNetworkInterface> existingNics;
    private List<NetworkAttachment> existingAttachments;
    private List<HostNetwork> networksToConfigure;
    private BusinessEntityMap<VdsNetworkInterface> existingNicsBusinessEntityMap;
    private final QosDaoCache qosDaoCache = new QosDaoCache(getDbFacade().getHostNetworkQosDao());

    @Inject
    private HostNetworkTopologyPersister hostNetworkTopologyPersister;

    @Inject
    private ManagementNetworkUtil managementNetworkUtil;

    @Inject
    private HostNetworkQosDao qosDao;
    private List<Network> modifiedNetworks;

    @Inject
    private NetworkClusterDao networkClusterDao;

    @Inject
    private NetworkAttachmentDao networkAttachmentDao;

    @Inject
    private NetworkDao networkDao;

    @Inject
    private VdsDao vdsDao;

    @Inject
    private NetworkIdNetworkNameCompleter networkIdNetworkNameCompleter;

    public HostSetupNetworksCommand(T parameters) {
        this(parameters, null);
    }

    public HostSetupNetworksCommand(T parameters, CommandContext commandContext) {
        super(parameters, commandContext);
        setVdsId(parameters.getVdsId());
    }

    @Override
    protected void setActionMessageParameters() {
        addCanDoActionMessage(EngineMessage.VAR__ACTION__SETUP);
        addCanDoActionMessage(EngineMessage.VAR__TYPE__NETWORKS);
    }

    @Override
    protected boolean canDoAction() {
        VDS host = getVds();

        final ValidationResult hostValidatorResult = new HostValidator(host, isInternalExecution()).validate();
        if (!hostValidatorResult.isValid()) {
            return validate(hostValidatorResult);
        }

        NicNameNicIdCompleter nicNameNicIdCompleter = new NicNameNicIdCompleter(getExistingNics());
        nicNameNicIdCompleter.completeNetworkAttachments(getParameters().getNetworkAttachments());
        nicNameNicIdCompleter.completeBonds(getParameters().getBonds());
        nicNameNicIdCompleter.completeNetworkAttachments(getExistingAttachments());

        networkIdNetworkNameCompleter.completeNetworkAttachments(
            getParameters().getNetworkAttachments(),
            getNetworkBusinessEntityMap());

        ValidationResult hostSetupNetworkValidatorResult = validateWithHostSetupNetworksValidator(host);
        if (!hostSetupNetworkValidatorResult.isValid()) {
            return validate(hostSetupNetworkValidatorResult);
        }


        return validate(checkForOutOfSyncNetworks());
    }

    private ValidationResult validateWithHostSetupNetworksValidator(VDS host) {
        HostSetupNetworksValidator validator = new HostSetupNetworksValidator(host,
            getParameters(),
            getExistingNics(),
            getExistingAttachments(),
            getNetworkBusinessEntityMap(),
            managementNetworkUtil,
            networkClusterDao,
            networkAttachmentDao,
            networkDao,
            vdsDao);

        return validator.validate();
    }

    @Override
    protected void executeCommand() {
        if (noChangesDetected()) {
            log.info("No changes were detected in setup networks for host '{}' (ID: '{}')", getVdsName(), getVdsId());
            setSucceeded(true);
            return;
        }

        int timeout = getSetupNetworksTimeout();
        FutureVDSCall<VDSReturnValue> setupNetworksTask = invokeSetupNetworksCommand(timeout);

        try {
            VDSReturnValue retVal = setupNetworksTask.get(timeout, TimeUnit.SECONDS);
            if (retVal != null) {
                if (!retVal.getSucceeded() && retVal.getVdsError() == null && getParameters().rollbackOnFailure()) {
                    throw new EngineException(EngineError.SETUP_NETWORKS_ROLLBACK, retVal.getExceptionString());
                }

                VdsHandler.handleVdsResult(retVal);

                if (retVal.getSucceeded()) {
                    try (EngineLock monitoringLock = acquireMonitorLock()) {
                        VDSReturnValue returnValue =
                            runVdsCommand(VDSCommandType.GetCapabilities,
                                new VdsIdAndVdsVDSCommandParametersBase(getVds()));
                        VDS updatedHost = (VDS) returnValue.getReturnValue();
                        persistNetworkChanges(updatedHost);
                    }
                    logMonitorLockReleased("Host setup networks");

                    setSucceeded(true);
                }
            }
        } catch (TimeoutException e) {
            log.debug("Host Setup networks command timed out for {} seconds", timeout);
        }
    }

    private ValidationResult checkForOutOfSyncNetworks() {
        BusinessEntityMap<VdsNetworkInterface> existingNicsBusinessEntityMap = getExistingNicsBusinessEntityMap();

        for (NetworkAttachment networkAttachment : getParameters().getNetworkAttachments()) {
            boolean newNetworkAttachment = networkAttachment.getId() == null;
            if (newNetworkAttachment) {
                //attachment to be yet created cannot be out of sync.
                continue;
            }

            boolean doNotCheckForOutOfSync = networkAttachment.isOverrideConfiguration();
            if (doNotCheckForOutOfSync) {
                continue;
            }

            Map<Guid, NetworkAttachment> existingNetworkAttachmentMap =
                Entities.businessEntitiesById(getExistingAttachments());
            NetworkAttachment existingNetworkAttachment = existingNetworkAttachmentMap.get(networkAttachment.getId());

            VdsNetworkInterface vdsNetworkInterface = existingNicsBusinessEntityMap.get(existingNetworkAttachment.getNicId());
            Network network = getNetworkBusinessEntityMap().get(existingNetworkAttachment.getNetworkId());
            HostNetworkQos qos = qosDaoCache.get(network.getQosId());

            boolean networkInSync = NetworkUtils.isNetworkInSync(vdsNetworkInterface, network, qos);
            if (!networkInSync) {
                return new ValidationResult(EngineMessage.NETWORKS_NOT_IN_SYNC, "NETWORK_NOT_IN_SYNC", network.getName());
            }
        }

        return ValidationResult.VALID;
    }

    private FutureVDSCall<VDSReturnValue> invokeSetupNetworksCommand(int timeout) {
        final HostSetupNetworksVdsCommandParameters parameters = createSetupNetworksParameters(timeout);
        FutureVDSCall<VDSReturnValue> setupNetworksTask =
            getBackend().getResourceManager().runFutureVdsCommand(FutureVDSCommandType.HostSetupNetworks,
                parameters);

        if (parameters.isRollbackOnFailure()) {
            HostSetupNetworkPoller poller = new HostSetupNetworkPoller();
            while (!setupNetworksTask.isDone()) {
                poller.poll(getVdsId());
            }
        }

        return setupNetworksTask;
    }

    private HostSetupNetworksVdsCommandParameters createSetupNetworksParameters(int timeout) {
        final HostSetupNetworksVdsCommandParameters hostCmdParams = new HostSetupNetworksVdsCommandParameters(
            getVds(),
            getNetworksToConfigure(),
            getAllNetworksToRemove(),
            getParameters().getBonds(),
            getRemovedBondNames());
        hostCmdParams.setRollbackOnFailure(getParameters().rollbackOnFailure());
        hostCmdParams.setConectivityTimeout(timeout);
        boolean hostNetworkQosSupported = FeatureSupported.hostNetworkQos(getVds().getVdsGroupCompatibilityVersion());
        hostCmdParams.setHostNetworkQosSupported(hostNetworkQosSupported);
        return hostCmdParams;
    }

    private Set<String> getAllNetworksToRemove() {
        Set<String> result = new HashSet<>(getRemovedNetworks().size() + getRemovedUnmanagedNetworks().size());
        result.addAll(getRemovedNetworks());
        result.addAll(getRemovedUnmanagedNetworks());
        return result;
    }

    protected Integer getSetupNetworksTimeout() {
        return getParameters().getConectivityTimeout() != null ? getParameters().getConectivityTimeout()
            : Config.<Integer> getValue(ConfigValues.NetworkConnectivityCheckTimeoutInSeconds);
    }

    private boolean defaultRouteRequired(Network network, IpConfiguration ipConfiguration) {
        return managementNetworkUtil.isManagementNetwork(network.getId(), getVds().getVdsGroupId())
                && ipConfiguration != null
                && ipConfiguration.hasPrimaryAddressSet()
                && (ipConfiguration.getPrimaryAddress().getBootProtocol() == NetworkBootProtocol.DHCP
                      || ipConfiguration.getPrimaryAddress().getBootProtocol() == NetworkBootProtocol.STATIC_IP
                            && StringUtils.isNotEmpty(ipConfiguration.getPrimaryAddress().getGateway()));
    }

    private boolean noChangesDetected() {
        return getNetworksToConfigure().isEmpty()
            && getRemovedNetworks().isEmpty()
            && getParameters().getBonds().isEmpty()
            && getRemovedBondNames().isEmpty()
            && getRemovedUnmanagedNetworks().isEmpty();
    }

    private List<VdsNetworkInterface> getRemovedBonds() {
        if (removedBonds == null) {
            Set<Guid> removedBondIds = getParameters().getRemovedBonds();
            removedBonds = Entities.filterEntitiesByRequiredIds(removedBondIds, getExistingNics());
        }

        return removedBonds;
    }

    private Set<String> getRemovedBondNames() {
        if (removedBondNames == null) {


            List<VdsNetworkInterface> existingVdsInterfaceToBeRemoved = getRemovedBonds();
            Set<String> removedBondNames = new HashSet<>(existingVdsInterfaceToBeRemoved.size());
            for (VdsNetworkInterface removedBondInterface : existingVdsInterfaceToBeRemoved) {
                removedBondNames.add(removedBondInterface.getName());
            }
            this.removedBondNames = removedBondNames;
        }

        return removedBondNames;
    }

    private List<VdsNetworkInterface> getExistingNics() {
        if (existingNics == null) {
            existingNics = getDbFacade().getInterfaceDao().getAllInterfacesForVds(getVdsId());

            for (VdsNetworkInterface iface : existingNics) {
                Network network = getNetworkBusinessEntityMap().get(iface.getNetworkName());
                HostNetworkQos hostNetworkQos = network == null ? null : qosDaoCache.get(network.getQosId());
                VdsNetworkInterface.NetworkImplementationDetails networkImplementationDetails =
                    NetworkUtils.calculateNetworkImplementationDetails(network, hostNetworkQos, iface);
                iface.setNetworkImplementationDetails(networkImplementationDetails);
            }
        }

        return existingNics;
    }

    private List<NetworkAttachment> getExistingAttachments() {
        if (existingAttachments == null) {
            existingAttachments = getDbFacade().getNetworkAttachmentDao().getAllForHost(getVdsId());
        }

        return existingAttachments;
    }

    private Set<String> getRemovedNetworks() {
        if (removedNetworks == null) {
            List<NetworkAttachment> removedNetworkAttachments =
                Entities.filterEntitiesByRequiredIds(getParameters().getRemovedNetworkAttachments(),
                    existingAttachments);
            removedNetworks = new HashSet<>(removedNetworkAttachments.size());

            for (NetworkAttachment attachment : removedNetworkAttachments) {
                removedNetworks.add(existingNetworkRelatedToAttachment(attachment).getName());
            }
        }

        return removedNetworks;
    }

    private Set<String> getRemovedUnmanagedNetworks() {
        if (removedUnmanagedNetworks == null) {
            this.removedUnmanagedNetworks = new HashSet<>(getParameters().getRemovedUnmanagedNetworks());
        }

        return removedUnmanagedNetworks;
    }

    private List<HostNetwork> getNetworksToConfigure() {
        if (networksToConfigure == null) {
            networksToConfigure = new ArrayList<>(getParameters().getNetworkAttachments().size());
            BusinessEntityMap<VdsNetworkInterface> nics = getExistingNicsBusinessEntityMap();

            for (NetworkAttachment attachment : getParameters().getNetworkAttachments()) {
                Network network = existingNetworkRelatedToAttachment(attachment);
                HostNetwork networkToConfigure = new HostNetwork(network, attachment);
                networkToConfigure.setBonding(isBonding(attachment, nics));

                if (defaultRouteSupported() && defaultRouteRequired(network, attachment.getIpConfiguration())) {
                    networkToConfigure.setDefaultRoute(true);
                }

                //TODO MM:  if it's newly created interface, it won't be discovered and Qos cannot be evaluated.
                VdsNetworkInterface iface = nics.get(attachment.getNicId(), attachment.getNicName());
                boolean qosConfiguredOnInterface =
                    iface == null ? false : NetworkUtils.qosConfiguredOnInterface(iface, network);
                networkToConfigure.setQosConfiguredOnInterface(qosConfiguredOnInterface);
                if (qosConfiguredOnInterface) {
                    networkToConfigure.setQos(
                        iface.isQosOverridden() ? iface.getQos() : qosDaoCache.get(network.getQosId()));
                }

                networksToConfigure.add(networkToConfigure);
            }
        }

        return networksToConfigure;
    }

    private BusinessEntityMap<VdsNetworkInterface> getExistingNicsBusinessEntityMap() {
        if (existingNicsBusinessEntityMap == null) {
            existingNicsBusinessEntityMap = new BusinessEntityMap<>(getExistingNics());
        }

        return existingNicsBusinessEntityMap;
    }

    private boolean defaultRouteSupported() {
        boolean defaultRouteSupported = false;
        Set<Version> supportedClusterVersionsSet = getVds().getSupportedClusterVersionsSet();
        if (supportedClusterVersionsSet == null || supportedClusterVersionsSet.isEmpty()) {
            log.warn("Host '{}' ('{}') doesn't contain Supported Cluster Versions, "
                    + "therefore 'defaultRoute' will not be sent via the SetupNetworks",
                getVdsName(),
                getVdsId());
        } else if (FeatureSupported.defaultRoute(Collections.max(supportedClusterVersionsSet))) {
            defaultRouteSupported = true;
        }

        return defaultRouteSupported;
    }

    private boolean isBonding(NetworkAttachment attachment, BusinessEntityMap<VdsNetworkInterface> nics) {
        for (Bond bond : getParameters().getBonds()) {
            if (bond.getName() != null && bond.getName().equals(attachment.getNicName())) {
                return true;
            }
        }

        VdsNetworkInterface attachedNic = nics.get(attachment.getNicId(), attachment.getNicName());
        Validate.notNull(attachedNic, "NicId/NicName must refer to a resolvable interface");
        return Boolean.TRUE.equals(attachedNic.getBonded());
    }

    private List<VdsNetworkInterface> applyUserConfiguredNics() {
        List<VdsNetworkInterface> userConfiguredNics = new ArrayList<>();
        userConfiguredNics.addAll(getParameters().getBonds());
        for (VdsNetworkInterface existingBondToRemove : getRemovedBonds()) {
            existingBondToRemove.setLabels(null);
            userConfiguredNics.add(existingBondToRemove);
        }

        return userConfiguredNics;
    }

    private List<Network> getModifiedNetworks() {
        if (modifiedNetworks == null) {
            List<NetworkAttachment> networkAttachments = getParameters().getNetworkAttachments();
            modifiedNetworks = new ArrayList<>(networkAttachments.size());

            for (NetworkAttachment attachment : networkAttachments) {
                modifiedNetworks.add(existingNetworkRelatedToAttachment(attachment));
            }
        }

        return modifiedNetworks;
    }

    private Network existingNetworkRelatedToAttachment(NetworkAttachment attachment) {
        return getNetworkBusinessEntityMap().get(attachment.getNetworkId());
    }

    private void persistNetworkChanges(final VDS updatedHost) {
        TransactionSupport.executeInNewTransaction(new TransactionMethod<Void>() {
            @Override
            public Void runInTransaction() {
                UserConfiguredNetworkData userConfiguredNetworkData =
                    new UserConfiguredNetworkData(getParameters().getNetworkAttachments(),
                        applyUserConfiguredNics());

                // save the new network topology to DB
                hostNetworkTopologyPersister.persistAndEnforceNetworkCompliance(updatedHost,
                    false,
                    userConfiguredNetworkData);

                getVdsDynamicDao().updateNetConfigDirty(getVds().getId(), getVds().getNetConfigDirty());

                // Update cluster networks (i.e. check if need to activate each new network)
                for (Network net : getModifiedNetworks()) {
                    NetworkClusterHelper.setStatus(getVdsGroupId(), net);
                }

                return null;
            }
        });
    }

    private BusinessEntityMap<Network> getNetworkBusinessEntityMap() {
        if (networkBusinessEntityMap == null) {
            List<Network> networks = getNetworkDao().getAllForCluster(getVdsGroupId());
            networkBusinessEntityMap = new BusinessEntityMap<>(networks);
        }

        return networkBusinessEntityMap;
    }

}
