/**
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and/or its affiliates, and individual
 * contributors by the @authors tag. See the copyright.txt in the
 * distribution for a full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.seam.reports.openoffice;

import static org.junit.Assert.assertTrue;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.jboss.arquillian.api.Deployment;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.seam.reports.Report;
import org.jboss.seam.reports.ReportDefinition;
import org.jboss.seam.reports.ReportLoader;
import org.jboss.seam.reports.ReportRenderer;
import org.jboss.seam.reports.openoffice.framework.OdfToolkitFacade;
import org.jboss.seam.reports.openoffice.framework.contenthandler.IterationHandler;
import org.jboss.seam.reports.openoffice.framework.contenthandler.OODefaultTableRowIterator;
import org.jboss.seam.reports.openoffice.framework.contenthandler.context.IterationContext;
import org.jboss.seam.reports.openoffice.model.User;
import org.jboss.seam.reports.output.PDF;
import org.jboss.seam.solder.resourceLoader.Resource;
import org.jboss.shrinkwrap.api.ArchivePaths;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.odftoolkit.simple.TextDocument;
import org.odftoolkit.simple.table.Table;
import org.testng.annotations.Test;

public class OOReportsTableTest extends Arquillian {

    @Inject
    @Resource("tableTemplate.odf")
    InputStream input;
    
    @Inject
    @Resource("nestedTableTemplate.odt")
    InputStream nestedTemplateInput;

    @Inject
    @Resource("facsimile.png")
    URL backgroundImage;

    @Inject
    @OOReports
    ReportLoader reportLoader;

    @Inject
    @OOReports
    ReportRenderer renderer;

    @Inject
    @OOReports
    @PDF
    ReportRenderer pdfRenderer;

    @Deployment
    public static JavaArchive createArchive() {
        return ShrinkWrap.create(JavaArchive.class).addPackages(true, "org.jboss.seam.solder")
                .addPackages(true, "org.jboss.seam.config").addPackages(true, "org.jboss.seam.reports")
                .addAsManifestResource(EmptyAsset.INSTANCE, ArchivePaths.create("beans.xml"))
                .addAsManifestResource("seam-beans.xml", ArchivePaths.create("seam-beans.xml"));
    }

    private List<User> createUserList() {
        List<User> result = new ArrayList<User>();
        result.add(new User("Alberto", "Gori").addItems("AA", "BB", "CC"));
        result.add(new User("James", "Lee").addItems("DD, FF, GG"));
        result.add(new User("Jane", "Pitt").addItems("GG", "II"));
        return result;
    }

    Report processTemplate() {
        Report report = reportLoader.loadReport(input);

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("name", "Alberto Gori");

        OdfToolkitFacade document = (OdfToolkitFacade) report.getDelegate();
        OOSeamReportDataSource ds = new OOSeamReportDataSource();
        ds.add(new OODefaultTableRowIterator<User>("table1", createUserList()));

        ReportDefinition reportDefinition = report.getReportDefinition();
        reportDefinition.fill(ds, parameters);
        return report;
    }

    @Test
    public void fill() throws Exception {
        Report report = processTemplate();
        FileOutputStream fos = new FileOutputStream("target/output.odf");
        renderer.render(report, fos);
        fos.close();

        TextDocument tester = TextDocument.loadDocument("target/output.odf");
        assertTrue(tester.getContentRoot().toString().contains("Alberto Gori"));
    }

    @Test
    public void hide() throws Exception {

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("name", "Alberto Gori");

        Report report = reportLoader.loadReport(input);
        OdfToolkitFacade document = (OdfToolkitFacade) report.getDelegate();

        OOSeamReportDataSource ds = new OOSeamReportDataSource();
        ds.add(new OODefaultTableRowIterator<User>("table1", createUserList()).hide());
        ReportDefinition reportDefinition = report.getReportDefinition();
        reportDefinition.fill(ds, parameters);

        FileOutputStream fos = new FileOutputStream("target/hidden-output.odf");
        renderer.render(report, fos);
        fos.close();

        TextDocument tester = TextDocument.loadDocument("target/output.odf");
        assertTrue(tester.getContentRoot().toString().contains("Alberto Gori"));
    }
    
    @Test
    public void fillNestedTables() throws Exception {

        Report report = reportLoader.loadReport(nestedTemplateInput);

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("name", "Alberto Gori");

        OOSeamReportDataSource ds = new OOSeamReportDataSource();
        OODefaultTableRowIterator<User> tableHandler = new OODefaultTableRowIterator<User>("table1", createUserList());
        tableHandler.setIterationHandler(new IterationHandler<User>() {
            
            @Override 
            public void afterIteration(IterationContext context, User value) {
                Table table = context.getTable("table11");
                OODefaultTableRowIterator<String> tableHandler = new OODefaultTableRowIterator<String>(table.getTableName(), value.getItems());
                context.add(tableHandler);
            }
            
        });
        ds.add(tableHandler);

        ReportDefinition reportDefinition = report.getReportDefinition();
        reportDefinition.fill(ds, parameters);
        
        final String filename = "target/nestedTableTemplate_fill.odt";
        FileOutputStream fos = new FileOutputStream(filename);
        renderer.render(report, fos);
        fos.close();

        TextDocument tester = TextDocument.loadDocument(filename);
        assertTrue(tester.getContentRoot().toString().contains("Alberto Gori"));
        assert tester.getTableList().size() == 4;
    }


    @Test(groups="openoffice")
    public void fillRenderPdf() throws Exception {
        Report report = processTemplate();
        FileOutputStream fos = new FileOutputStream("target/output.pdf");
        pdfRenderer.render(report, fos);
        fos.close();
    }

}
