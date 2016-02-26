/**
 * Copyright (c) 2010 committers of YAKINDU and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributors:
 * 	committers of YAKINDU - initial API and implementation
 * 
 */
package org.yakindu.sct.ui.editor.wizards;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.workspace.util.WorkspaceSynchronizer;
import org.eclipse.gef.requests.DirectEditRequest;
import org.eclipse.gmf.runtime.common.core.command.CommandResult;
import org.eclipse.gmf.runtime.diagram.core.preferences.PreferencesHint;
import org.eclipse.gmf.runtime.diagram.ui.editparts.IGraphicalEditPart;
import org.eclipse.gmf.runtime.emf.commands.core.command.AbstractTransactionalCommand;
import org.eclipse.gmf.runtime.emf.core.GMFEditingDomainFactory;
import org.eclipse.gmf.runtime.notation.View;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.xtext.EcoreUtil2;
import org.yakindu.base.base.BasePackage;
import org.yakindu.base.gmf.runtime.util.EditPartUtils;
import org.yakindu.sct.domain.extension.DomainRegistry;
import org.yakindu.sct.model.sgraph.SGraphPackage;
import org.yakindu.sct.model.sgraph.Statechart;
import org.yakindu.sct.ui.editor.DiagramActivator;
import org.yakindu.sct.ui.editor.StatechartImages;
import org.yakindu.sct.ui.editor.editor.StatechartDiagramEditor;
import org.yakindu.sct.ui.editor.factories.FactoryUtils;
import org.yakindu.sct.ui.editor.providers.SemanticHints;
import org.yakindu.sct.ui.perspectives.PerspectiveUtil;
import org.yakindu.sct.ui.wizards.ModelCreationWizardPage;

/**
 * 
 * @author andreas muelder - Initial contribution and API
 * 
 */
public class CreationWizard extends Wizard implements INewWizard {

	public static final String ID = "org.yakindu.sct.ui.editor.StatechartDiagramWizard";

	protected IStructuredSelection selection = new StructuredSelection();

	protected ModelCreationWizardPage modelCreationPage;

	protected DomainWizardPage domainWizardPage;

	protected Resource diagram;

	private boolean openOnCreate = true;

	protected PreferencesHint preferencesHint = DiagramActivator.DIAGRAM_PREFERENCES_HINT;

	private IWorkbench workbench;

	protected SCTDiagramHelper diagramHelper;

	public void init(IWorkbench workbench, IStructuredSelection selection) {
		this.workbench = workbench;
		this.selection = selection;
		setWindowTitle("New YAKINDU Statechart");
		setNeedsProgressMonitor(true);
		diagramHelper = new SCTDiagramHelper();
	}

	@Override
	public void addPages() {
		modelCreationPage = new ModelCreationWizardPage("DiagramModelFile", getSelection(), "sct");
		modelCreationPage.setTitle("YAKINDU SCT Diagram");
		modelCreationPage.setDescription("Create a new YAKINDU SCT Diagram File");
		modelCreationPage.setImageDescriptor(StatechartImages.LOGO.imageDescriptor());
		if (DomainRegistry.getDomainDescriptors().size() > 1) {
			domainWizardPage = new DomainWizardPage("DomainWizard");
			domainWizardPage.setTitle("Select Statechart Domain");
			domainWizardPage.setDescription("Select the domain you want to create a statechart for.");
			domainWizardPage.setImageDescriptor(StatechartImages.LOGO.imageDescriptor());
			addPage(domainWizardPage);
		}

		addPage(modelCreationPage);
	}

	@Override
	public boolean performFinish() {
		IRunnableWithProgress op = new WorkspaceModifyOperation(null) {
			@Override
			protected void execute(IProgressMonitor monitor) throws CoreException, InterruptedException {
				diagram = createDiagram(modelCreationPage.getURI(), modelCreationPage.getURI(), monitor);
				if (isOpenOnCreate() && diagram != null) {
					try {
						openDiagram(diagram);
						PerspectiveUtil.switchToModelingPerspective(workbench.getActiveWorkbenchWindow());
					} catch (PartInitException e) {
						DiagramActivator.getDefault().getLog().log(
								new Status(IStatus.WARNING, DiagramActivator.PLUGIN_ID, "Editor can't be opened", e));
					}
				}
			}
		};
		try {
			getContainer().run(false, true, op);
		} catch (Exception e) {
			return false;
		}
		return diagram != null;
	}

	protected boolean openDiagram(Resource diagram) throws PartInitException {
		return diagramHelper.openDiagram(diagram,getEditorID(),getContainer());
	}

	private Resource createDiagram(URI uri, URI uri2, IProgressMonitor monitor) {
		return diagramHelper.createDefaultDiagram(uri, domainWizardPage.getDomainID(),getContainer()).eResource();
	}

	public IStructuredSelection getSelection() {
		return selection;
	}

	public void setSelection(IStructuredSelection selection) {
		this.selection = selection;
	}

	public boolean isOpenOnCreate() {
		return openOnCreate;
	}

	public void setOpenOnCreate(boolean openOnCreate) {
		this.openOnCreate = openOnCreate;
	}
	
	/**
	 * Returns the Editor ID of the editor to open, after a new diagram was
	 * created Override for subclasses with custom editors.
	 * 
	 * @return the ID of the editor.
	 */
	protected String getEditorID() {
		return StatechartDiagramEditor.ID;
	}

	/**
	 * Set the preference hint for preferences of new created diagrams
	 * 
	 * @param hint
	 *            the PreferenceHint for the PreferenceStore (mostly from
	 *            Activator)
	 */
	protected void setPreferenceHint(PreferencesHint hint) {
		preferencesHint = hint;
	}

}
