package org.ovirt.engine.core.common;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ovirt.engine.core.common.action.VdcActionType;
import org.ovirt.engine.core.common.businessentities.StorageDomain;
import org.ovirt.engine.core.common.businessentities.StorageDomainStatus;
import org.ovirt.engine.core.common.businessentities.VDS;
import org.ovirt.engine.core.common.businessentities.VDSStatus;
import org.ovirt.engine.core.common.businessentities.VM;
import org.ovirt.engine.core.common.businessentities.VMStatus;
import org.ovirt.engine.core.common.businessentities.VmTemplate;
import org.ovirt.engine.core.common.businessentities.VmTemplateStatus;
import org.ovirt.engine.core.compat.NotImplementedException;

public final class VdcActionUtils {

    private static final Map<Class<?>, Map<Enum<?>, Set<VdcActionType>>> _matrix =
            new HashMap<Class<?>, Map<Enum<?>, Set<VdcActionType>>>();

    static {
        // this matrix contains the actions that CANNOT run per status
        // ("black list")
        Map<Enum<?>, Set<VdcActionType>> vdsMatrix = new HashMap<Enum<?>, Set<VdcActionType>>();
        vdsMatrix.put(
                VDSStatus.Maintenance,
                EnumSet.of(VdcActionType.MaintenanceVds, VdcActionType.ClearNonResponsiveVdsVms,
                        VdcActionType.ApproveVds));
        vdsMatrix.put(
                VDSStatus.Up,
                EnumSet.of(VdcActionType.ActivateVds, VdcActionType.RemoveVds,
                        VdcActionType.ClearNonResponsiveVdsVms,
                        VdcActionType.ApproveVds, VdcActionType.StartVds, VdcActionType.StopVds));
        vdsMatrix.put(
                VDSStatus.Error,
                EnumSet.of(VdcActionType.RemoveVds,
                        VdcActionType.ClearNonResponsiveVdsVms,
                        VdcActionType.ApproveVds,
                        VdcActionType.RefreshHostCapabilities));
        vdsMatrix.put(
                VDSStatus.Installing,
                EnumSet.of(VdcActionType.RemoveVds, VdcActionType.ActivateVds,
                        VdcActionType.ClearNonResponsiveVdsVms,
                        VdcActionType.ApproveVds, VdcActionType.MaintenanceVds, VdcActionType.StartVds,
                        VdcActionType.StopVds, VdcActionType.RefreshHostCapabilities));
        vdsMatrix.put(
                VDSStatus.NonResponsive,
                EnumSet.of(VdcActionType.RemoveVds, VdcActionType.ActivateVds,
                        VdcActionType.ApproveVds, VdcActionType.RefreshHostCapabilities));
        vdsMatrix.put(
                VDSStatus.PreparingForMaintenance,
                EnumSet.of(VdcActionType.RemoveVds, VdcActionType.MaintenanceVds,
                        VdcActionType.ClearNonResponsiveVdsVms,
                        VdcActionType.ApproveVds, VdcActionType.RefreshHostCapabilities));
        vdsMatrix.put(
                VDSStatus.Reboot,
                EnumSet.of(VdcActionType.ActivateVds, VdcActionType.RemoveVds,
                        VdcActionType.ClearNonResponsiveVdsVms,
                        VdcActionType.ApproveVds, VdcActionType.MaintenanceVds, VdcActionType.RefreshHostCapabilities));
        vdsMatrix.put(
                VDSStatus.Unassigned,
                EnumSet.of(VdcActionType.ActivateVds,
                        VdcActionType.RemoveVds,
                        VdcActionType.MaintenanceVds,
                        VdcActionType.ClearNonResponsiveVdsVms,
                        VdcActionType.ApproveVds,
                        VdcActionType.RefreshHostCapabilities));
        vdsMatrix.put(
                VDSStatus.Initializing,
                EnumSet.of(VdcActionType.ActivateVds, VdcActionType.RemoveVds,
                        VdcActionType.ClearNonResponsiveVdsVms, VdcActionType.ApproveVds,
                        VdcActionType.MaintenanceVds, VdcActionType.RefreshHostCapabilities));
        vdsMatrix.put(
                VDSStatus.NonOperational,
                EnumSet.of(VdcActionType.RemoveVds,
                        VdcActionType.ApproveVds,
                        VdcActionType.RefreshHostCapabilities));
        vdsMatrix.put(
                VDSStatus.PendingApproval,
                EnumSet.of(VdcActionType.UpdateVds,
                        VdcActionType.ActivateVds, VdcActionType.MaintenanceVds,
                        VdcActionType.AttachVdsToTag,
                        VdcActionType.ClearNonResponsiveVdsVms,
                        VdcActionType.RefreshHostCapabilities));
        vdsMatrix.put(
                VDSStatus.InstallFailed,
                EnumSet.of(VdcActionType.ApproveVds, VdcActionType.RefreshHostCapabilities));
        vdsMatrix.put(
                VDSStatus.Connecting,
                EnumSet.of(VdcActionType.MaintenanceVds, VdcActionType.RemoveVds,
                        VdcActionType.ActivateVds, VdcActionType.ApproveVds, VdcActionType.RefreshHostCapabilities));
        vdsMatrix.put(
                VDSStatus.Down,
                EnumSet.of(VdcActionType.ActivateVds, VdcActionType.ApproveVds, VdcActionType.RefreshHostCapabilities));
        _matrix.put(VDS.class, vdsMatrix);

        Map<Enum<?>, Set<VdcActionType>> vmMatrix = new HashMap<Enum<?>, Set<VdcActionType>>();
        vmMatrix.put(
                VMStatus.WaitForLaunch,
                EnumSet.of(VdcActionType.HibernateVm, VdcActionType.RunVm,
                        VdcActionType.RunVmOnce, VdcActionType.AddVmTemplate, VdcActionType.RemoveVm,
                        VdcActionType.ExportVm, VdcActionType.MoveVm, VdcActionType.ImportVm, VdcActionType.ChangeDisk,
                        VdcActionType.AddVmInterface, VdcActionType.UpdateVmInterface,
                        VdcActionType.RemoveVmInterface, VdcActionType.CancelMigrateVm,
                        VdcActionType.ExtendImageSize));
        vmMatrix.put(
                VMStatus.Up,
                EnumSet.of(VdcActionType.RunVm,
                        VdcActionType.RunVmOnce, VdcActionType.AddVmTemplate, VdcActionType.RemoveVm,
                        VdcActionType.ExportVm, VdcActionType.MoveVm, VdcActionType.ImportVm,
                        VdcActionType.CancelMigrateVm));
        vmMatrix.put(
                VMStatus.PoweringDown,
                EnumSet.of(VdcActionType.HibernateVm, VdcActionType.RunVm,
                        VdcActionType.RunVmOnce,
                        VdcActionType.AddVmTemplate, VdcActionType.RemoveVm, VdcActionType.MigrateVm,
                        VdcActionType.ExportVm, VdcActionType.MoveVm, VdcActionType.ImportVm,
                        VdcActionType.ChangeDisk, VdcActionType.AddVmInterface,
                        VdcActionType.UpdateVmInterface,
                        VdcActionType.RemoveVmInterface, VdcActionType.CancelMigrateVm,
                        VdcActionType.ExtendImageSize));
        vmMatrix.put(
                VMStatus.PoweringUp,
                EnumSet.of(VdcActionType.HibernateVm, VdcActionType.RunVm,
                        VdcActionType.RunVmOnce, VdcActionType.AddVmTemplate, VdcActionType.RemoveVm,
                        VdcActionType.ExportVm, VdcActionType.MoveVm, VdcActionType.ImportVm, VdcActionType.ChangeDisk,
                        VdcActionType.AddVmInterface, VdcActionType.UpdateVmInterface,
                        VdcActionType.RemoveVmInterface, VdcActionType.CancelMigrateVm,
                        VdcActionType.ExtendImageSize));
        vmMatrix.put(
                VMStatus.RebootInProgress,
                EnumSet.of(VdcActionType.HibernateVm, VdcActionType.RunVm,
                        VdcActionType.RunVmOnce, VdcActionType.AddVmTemplate, VdcActionType.RemoveVm,
                        VdcActionType.ExportVm, VdcActionType.MoveVm, VdcActionType.ImportVm, VdcActionType.ChangeDisk,
                        VdcActionType.AddVmInterface, VdcActionType.UpdateVmInterface,
                        VdcActionType.RemoveVmInterface, VdcActionType.CancelMigrateVm,
                        VdcActionType.ExtendImageSize));
        vmMatrix.put(
                VMStatus.MigratingFrom,
                EnumSet.of(VdcActionType.RunVm,
                        VdcActionType.RunVmOnce, VdcActionType.AddVmTemplate, VdcActionType.RemoveVm,
                        VdcActionType.HibernateVm, VdcActionType.MigrateVm, VdcActionType.ExportVm,
                        VdcActionType.MoveVm, VdcActionType.ImportVm, VdcActionType.ChangeDisk,
                        VdcActionType.AddVmInterface, VdcActionType.UpdateVmInterface,
                        VdcActionType.RemoveVmInterface, VdcActionType.CreateAllSnapshotsFromVm,
                        VdcActionType.ExtendImageSize));
        vmMatrix.put(
                VMStatus.Suspended,
                EnumSet.of(VdcActionType.HibernateVm, VdcActionType.AddVmTemplate,
                        VdcActionType.RunVmOnce, VdcActionType.MigrateVm, VdcActionType.ExportVm, VdcActionType.MoveVm,
                        VdcActionType.ImportVm, VdcActionType.ChangeDisk, VdcActionType.RemoveVm,
                        VdcActionType.AddVmInterface, VdcActionType.UpdateVmInterface,
                        VdcActionType.RemoveVmInterface, VdcActionType.CancelMigrateVm));
        vmMatrix.put(
                VMStatus.Paused,
                EnumSet.of(VdcActionType.RemoveVm, VdcActionType.HibernateVm,
                        VdcActionType.AddVmTemplate, VdcActionType.RunVmOnce, VdcActionType.ExportVm,
                        VdcActionType.MoveVm, VdcActionType.ImportVm,
                        VdcActionType.AddVmInterface, VdcActionType.UpdateVmInterface,
                        VdcActionType.RemoveVmInterface, VdcActionType.CancelMigrateVm));
        vmMatrix.put(
                VMStatus.SavingState,
                EnumSet.of(VdcActionType.RunVm,
                        VdcActionType.RunVmOnce, VdcActionType.StopVm, VdcActionType.ShutdownVm,
                        VdcActionType.HibernateVm, VdcActionType.MigrateVm, VdcActionType.RemoveVm,
                        VdcActionType.AddVmTemplate, VdcActionType.ExportVm, VdcActionType.MoveVm,
                        VdcActionType.ImportVm, VdcActionType.ChangeDisk,
                        VdcActionType.AddVmInterface, VdcActionType.UpdateVmInterface,
                        VdcActionType.RemoveVmInterface, VdcActionType.CancelMigrateVm,
                        VdcActionType.ExtendImageSize));
        vmMatrix.put(
                VMStatus.PreparingForHibernate,
                EnumSet.of(VdcActionType.RunVm,
                        VdcActionType.RunVmOnce, VdcActionType.StopVm, VdcActionType.ShutdownVm,
                        VdcActionType.HibernateVm, VdcActionType.MigrateVm, VdcActionType.RemoveVm,
                        VdcActionType.AddVmTemplate, VdcActionType.ExportVm, VdcActionType.MoveVm,
                        VdcActionType.ImportVm, VdcActionType.ChangeDisk,
                        VdcActionType.AddVmInterface, VdcActionType.UpdateVmInterface,
                        VdcActionType.RemoveVmInterface, VdcActionType.CancelMigrateVm,
                        VdcActionType.ExtendImageSize));
        vmMatrix.put(
                VMStatus.RestoringState,
                EnumSet.of(VdcActionType.RunVm,
                        VdcActionType.RunVmOnce, VdcActionType.StopVm, VdcActionType.ShutdownVm,
                        VdcActionType.HibernateVm, VdcActionType.MigrateVm, VdcActionType.RemoveVm,
                        VdcActionType.AddVmTemplate, VdcActionType.ExportVm, VdcActionType.MoveVm,
                        VdcActionType.ImportVm, VdcActionType.ChangeDisk,
                        VdcActionType.AddVmInterface, VdcActionType.UpdateVmInterface,
                        VdcActionType.RemoveVmInterface, VdcActionType.CancelMigrateVm, VdcActionType.ExtendImageSize));

        vmMatrix.put(
                VMStatus.Down,
                EnumSet.of(VdcActionType.StopVm, VdcActionType.ShutdownVm,
                        VdcActionType.HibernateVm, VdcActionType.MigrateVm, VdcActionType.ChangeDisk,
                        VdcActionType.CancelMigrateVm));
        vmMatrix.put(
                VMStatus.ImageIllegal,
                EnumSet.of(VdcActionType.RunVm,
                        VdcActionType.RunVmOnce,
                        VdcActionType.StopVm,
                        VdcActionType.ShutdownVm,
                        VdcActionType.HibernateVm,
                        VdcActionType.MigrateVm,
                        VdcActionType.AddVmTemplate,
                        VdcActionType.ExportVm,
                        VdcActionType.MoveVm,
                        VdcActionType.ImportVm,
                        VdcActionType.ChangeDisk,
                        VdcActionType.AddVmInterface,
                        VdcActionType.UpdateVmInterface,
                        VdcActionType.CreateAllSnapshotsFromVm,
                        VdcActionType.RemoveVmInterface,
                        VdcActionType.CancelMigrateVm,
                        VdcActionType.ExtendImageSize));
        vmMatrix.put(
                VMStatus.ImageLocked,
                EnumSet.of(VdcActionType.RunVm,
                        VdcActionType.RunVmOnce, VdcActionType.StopVm, VdcActionType.ShutdownVm,
                        VdcActionType.HibernateVm, VdcActionType.MigrateVm, VdcActionType.RemoveVm,
                        VdcActionType.AddVmTemplate, VdcActionType.ExportVm, VdcActionType.MoveVm,
                        VdcActionType.ImportVm, VdcActionType.ChangeDisk, VdcActionType.CreateAllSnapshotsFromVm,
                        VdcActionType.AddVmInterface, VdcActionType.UpdateVmInterface,
                        VdcActionType.RemoveVmInterface, VdcActionType.CancelMigrateVm, VdcActionType.ExtendImageSize));
        vmMatrix.put(
                VMStatus.NotResponding,
                EnumSet.of(VdcActionType.RunVm,
                        VdcActionType.RunVmOnce, VdcActionType.HibernateVm, VdcActionType.MigrateVm,
                        VdcActionType.RemoveVm, VdcActionType.AddVmTemplate, VdcActionType.ExportVm,
                        VdcActionType.MoveVm, VdcActionType.ImportVm, VdcActionType.ChangeDisk,
                        VdcActionType.AddVmInterface, VdcActionType.UpdateVmInterface,
                        VdcActionType.RemoveVmInterface, VdcActionType.CancelMigrateVm, VdcActionType.ExtendImageSize));

        vmMatrix.put(
                VMStatus.Unassigned,
                EnumSet.of(VdcActionType.RunVm,
                        VdcActionType.RunVmOnce, VdcActionType.StopVm, VdcActionType.ShutdownVm,
                        VdcActionType.HibernateVm, VdcActionType.MigrateVm, VdcActionType.RemoveVm,
                        VdcActionType.AddVmTemplate, VdcActionType.ExportVm, VdcActionType.MoveVm,
                        VdcActionType.ImportVm, VdcActionType.ChangeDisk, VdcActionType.CreateAllSnapshotsFromVm,
                        VdcActionType.AddVmInterface, VdcActionType.UpdateVmInterface,
                        VdcActionType.RemoveVmInterface, VdcActionType.CancelMigrateVm, VdcActionType.ExtendImageSize));
        vmMatrix.put(
                VMStatus.Unknown,
                EnumSet.of(VdcActionType.RunVm,
                        VdcActionType.RunVmOnce, VdcActionType.StopVm, VdcActionType.ShutdownVm,
                        VdcActionType.HibernateVm, VdcActionType.MigrateVm, VdcActionType.RemoveVm,
                        VdcActionType.AddVmTemplate, VdcActionType.ExportVm, VdcActionType.MoveVm,
                        VdcActionType.ImportVm, VdcActionType.ChangeDisk, VdcActionType.CreateAllSnapshotsFromVm,
                        VdcActionType.AddVmInterface, VdcActionType.UpdateVmInterface,
                        VdcActionType.RemoveVmInterface, VdcActionType.CancelMigrateVm, VdcActionType.ExtendImageSize));
        _matrix.put(VM.class, vmMatrix);

        Map<Enum<?>, Set<VdcActionType>> vmTemplateMatrix = new HashMap<Enum<?>, Set<VdcActionType>>();
        vmTemplateMatrix.put(
                VmTemplateStatus.Locked,
                EnumSet.of(VdcActionType.RemoveVmTemplate,
                        VdcActionType.ExportVmTemplate,
                        VdcActionType.MoveOrCopyTemplate, VdcActionType.ImportVmTemplate));
        vmTemplateMatrix.put(
                VmTemplateStatus.Illegal,
                EnumSet.of(VdcActionType.ExportVmTemplate,
                        VdcActionType.MoveOrCopyTemplate, VdcActionType.ImportVmTemplate));
        _matrix.put(VmTemplate.class, vmTemplateMatrix);

        Map<Enum<?>, Set<VdcActionType>> storageDomainMatrix = new HashMap<Enum<?>, Set<VdcActionType>>();
        storageDomainMatrix.put(
                StorageDomainStatus.Active,
                EnumSet.of(VdcActionType.DetachStorageDomainFromPool, VdcActionType.ActivateStorageDomain));
        storageDomainMatrix.put(
                StorageDomainStatus.InActive,
                EnumSet.of(VdcActionType.DeactivateStorageDomain));
        storageDomainMatrix.put(
                StorageDomainStatus.Locked,
                EnumSet.of(VdcActionType.DetachStorageDomainFromPool,
                        VdcActionType.DeactivateStorageDomain, VdcActionType.ActivateStorageDomain));
        storageDomainMatrix.put(
                StorageDomainStatus.Unattached,
                EnumSet.of(VdcActionType.DetachStorageDomainFromPool,
                        VdcActionType.DeactivateStorageDomain, VdcActionType.ActivateStorageDomain));
        storageDomainMatrix.put(
                StorageDomainStatus.Uninitialized,
                EnumSet.of(VdcActionType.DetachStorageDomainFromPool,
                        VdcActionType.DeactivateStorageDomain, VdcActionType.ActivateStorageDomain));
        storageDomainMatrix.put(
                StorageDomainStatus.Unknown,
                EnumSet.of(VdcActionType.DetachStorageDomainFromPool, VdcActionType.DeactivateStorageDomain));
        storageDomainMatrix.put(
                StorageDomainStatus.Maintenance,
                EnumSet.of(VdcActionType.DeactivateStorageDomain));
        _matrix.put(StorageDomain.class, storageDomainMatrix);
    }

    public static boolean canExecute(List<?> entities, Class<?> type, VdcActionType action) {
        if (_matrix.containsKey(type)) {
            for (Object a : entities) {
                if (a.getClass() == type && _matrix.get(type).containsKey(getStatusProperty(a))
                        && _matrix.get(type).get(getStatusProperty(a)).contains(action)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Enum<?> getStatusProperty(Object entity) {
        if (entity.getClass().getName().endsWith("VDS")) {
            return entity instanceof VDS ?
                    ((VDS) entity).getStatus() :
                    null;
        }
        if (entity.getClass().getName().endsWith("VM")) {
            return entity instanceof VM ?
                    ((VM) entity).getStatus() :
                    null;
        }
        if (entity.getClass().getName().endsWith("VmTemplate")) {
            return entity instanceof VmTemplate ?
                    ((VmTemplate) entity).getStatus() :
                    null;

        }
        if (entity instanceof StorageDomain) {
            StorageDomainStatus status = ((StorageDomain) entity).getStatus();
            return status != null ? status : StorageDomainStatus.Uninitialized;
        }

        throw new NotImplementedException();
    }

}
