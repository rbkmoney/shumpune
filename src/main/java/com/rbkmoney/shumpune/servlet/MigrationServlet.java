package com.rbkmoney.shumpune.servlet;

import com.rbkmoney.damsel.shumpune.MigrationHelperSrv;
import com.rbkmoney.woody.thrift.impl.http.THServiceBuilder;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.*;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;

@WebServlet("/migration")
public class MigrationServlet extends GenericServlet {


    private Servlet thriftServlet;

    @Autowired
    private MigrationHelperSrv.Iface requestHandler;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        thriftServlet = new THServiceBuilder()
                .build(MigrationHelperSrv.Iface.class, requestHandler);
    }

    @Override
    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        thriftServlet.service(req, res);
    }

}
