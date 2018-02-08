<!DOCTYPE html>
<html lang="en">
<head>

    <meta charset="utf-8">
    <title>Knowledgeroot</title>
    <meta name="description" content="">
    <meta name="author" content="">

    <!-- Le HTML5 shim, for IE6-8 support of HTML elements -->
    <!--[if lt IE 9]>
    <script src="http://html5shim.googlecode.com/svn/trunk/html5.js"></script>
    <![endif]-->

    <!-- Bootstrap -->
    <link rel="stylesheet" href="webjars/bootstrap/css/bootstrap.min.css">

    <!-- Font Awesome -->
    <link rel="stylesheet" href="webjars/font-awesome/css/font-awesome.min.css">

    <link href="resources/css/base.css" rel="stylesheet">
    <style type="text/css">

        .colorme {
            /* IE10+ */
            background-image: -ms-linear-gradient(top right, #B0FFE2 0%, #23538A 100%);

            /* Mozilla Firefox */
            background-image: -moz-linear-gradient(top right, #B0FFE2 0%, #23538A 100%);

            /* Opera */
            background-image: -o-linear-gradient(top right, #B0FFE2 0%, #23538A 100%);

            /* Webkit (Safari/Chrome 10) */
            background-image: -webkit-gradient(linear, right top, left bottom, color-stop(0, #B0FFE2), color-stop(100, #23538A));

            /* Webkit (Chrome 11+) */
            background-image: -webkit-linear-gradient(top right, #B0FFE2 0%, #23538A 100%);

            /* W3C Markup */
            background-image: linear-gradient(to bottom left, #B0FFE2 0%, #23538A 100%);
        }

        html, body {
            width: 100%;
            height: 100%;
            padding: 0;
            margin: 0;
            overflow: auto;
        }

        body {
            padding-top: 60px;
            padding-bottom: 60px;
            background-color: #fdfdfd;
        }

        /* Footer
        -------------------------------------------------- */
        .footer {
            background-color: #eee;
            min-width: 940px;
            padding: 30px 0;
            text-shadow: 0 1px 0 #fff;
            border-top: 1px solid #e5e5e5;
            -webkit-box-shadow: inset 0 5px 15px rgba(0,0,0,.025);
            -moz-box-shadow: inset 0 5px 15px rgba(0,0,0,.025);
            /*          box-shadow: inset 0 5px 15px rgba(0,0,0,.025);
            */}
        .footer p {
            color: #555;
        }

        #sidebar-layout {
            background:	#999;
            height:		100%;
            margin:		0 auto;
            width:		100%;
        }
    </style>

    <!-- jQuery -->
    <script src="webjars/jquery/jquery.min.js"></script>

    <!-- jQuery ui -->
    <script src="webjars/jquery-ui/jquery-ui.min.js"></script>

    <!-- jQuery layout -->
    <script src="resources/jquery-layout/jquery.layout-latest.min.js"></script>

    <!-- Bootstrap -->
    <script src="webjars/bootstrap/js/bootstrap.min.js"></script>

    <!-- AngularJS -->
    <script src="webjars/angularjs/angular.min.js"></script>

    <!-- jstree -->
    <link rel="stylesheet" href="webjars/jstree/themes/default/style.min.css" />
    <script src="webjars/jstree/jstree.min.js"></script>

    <script type="text/javascript">
        $(document).ready(function() {
            $('#sidebar-layout').layout({
                minSize: 300,
                west__size: 300,
                stateManagement__enabled: true,
                stateManagement__cookie__path: "/"
            });
        });
    </script>

    <link type="text/css" rel="stylesheet" href="resources/jquery-layout/layout-default-latest.css" />
</head>
<body>

<nav class="navbar fixed-top navbar-dark bg-dark navbar-expand-lg" style="border-bottom: 3px solid #F88529;">
    <a class="navbar-brand" href="#"><i class="fa fa-lock" aria-hidden="true"></i> Knowledgeroot</a>
    <button class="navbar-toggler" type="button" data-toggle="collapse" data-target="#navbarText" aria-controls="navbarText" aria-expanded="false" aria-label="Toggle navigation">
        <span class="navbar-toggler-icon"></span>
    </button>
    <div class="collapse navbar-collapse" id="navbarText">
        <ul class="navbar-nav mr-auto">
            <li class="nav-item active">
                <a class="nav-link" href="#">Home <span class="sr-only">(current)</span></a>
            </li>
            <li class="nav-item">
                <a class="nav-link" href="#">Features</a>
            </li>
            <li class="nav-item">
                <a class="nav-link" href="#">Pricing</a>
            </li>
        </ul>
        <span class="navbar-text">
      Navbar text with an inline element
    </span>
    </div>
</nav>

<div id="sidebar-layout">
    <div class="ui-layout-west" style="background-color: #f5f5f5;">
        <div class="input-group">
            <div class="input-group-btn">
                <button class="btn btn-default" type="button" onclick="$('#treeOne').jstree(true).refresh();"><i class="fa fa-refresh"></i></button>
                <button class="btn btn-default" type="button" onclick="$('#treeOne').jstree('open_all');"><i class="fa fa-plus"></i></button>
                <button class="btn btn-default" type="button" onclick="$('#treeOne').jstree('close_all');"><i class="fa fa-minus"></i></button>
            </div>
            <input id="treeOneSearch" type="text" placeholder="Search" class="form-control">
        </div>
        <script type="text/javascript">
            $.getJSON( "tree.json", function( data ) {
                $(function () { $('#treeOne')
                        .on('changed.jstree', function (e, data) {
                            item = data.node.original;

                            if(item.alias != "")
                                location.href = item.alias;
                            else
                                location.href = item.url;
                        })
                        .jstree({
                            'core' : {
                                'data' : data.items
                            },
                            'sort': function(a, b) {
                                return this.get_node(a).original.sort > this.get_node(b).original.sort ? 1 : -1;
                            },
                            "plugins" : [ "sort", "state", "search" ]
                        }); });

                var to = false;
                $('#treeOneSearch').keyup(function () {
                    if(to)
                        clearTimeout(to);

                    to = setTimeout(function () {
                        var v = $('#treeOneSearch').val();
                        $('#treeOne').jstree(true).search(v);
                    }, 250);
                });
            });
        </script>

        <div id="treeOne"></div>
    </div>

    <div class="ui-layout-center">

        <div class="content">
            <script>
                $(".alert").alert()
            </script>
            <div class="">
                <nav aria-label="breadcrumb">
                    <ol class="breadcrumb">
                        <li class="breadcrumb-item"><i class="fa fa-home fa-lg"></i> <a href="#">Home</a></li>
                        <li class="breadcrumb-item"><a href="#">Library</a></li>
                        <li class="breadcrumb-item active" aria-current="page">Data</li>
                    </ol>
                </nav>

                <div class="d-flex align-items-center p-3 my-3 text-white-50 bg-purple rounded box-shadow">
                    <img class="mr-3" src="https://getbootstrap.com/assets/brand/bootstrap-outline.svg" alt="" width="48" height="48">
                    <div class="lh-100">
                        <h6 class="mb-0 text-white lh-100">Bootstrap</h6>
                        <small>Since 2011</small>
                    </div>
                </div>

                <div class="card">
                    <div class="card-header">
                        <ul class="nav nav-pills card-header-pills">
                            <li class="nav-item">
                                <a class="nav-link active" href="#">new</a>
                            </li>
                        </ul>
                    </div>
                    <div class="card-body">
                        <table class="table table-sm">
                            <thead>
                            <tr>
                                <th scope="col">#</th>
                                <th scope="col">First</th>
                                <th scope="col">Last</th>
                                <th scope="col">Handle</th>
                            </tr>
                            </thead>
                            <tbody>
                            <tr>
                                <th scope="row">1</th>
                                <td>Mark</td>
                                <td>Otto</td>
                                <td>@mdo</td>
                            </tr>
                            <tr>
                                <th scope="row">2</th>
                                <td>Jacob</td>
                                <td>Thornton</td>
                                <td>@fat</td>
                            </tr>
                            <tr>
                                <th scope="row">3</th>
                                <td colspan="2">Larry the Bird</td>
                                <td>@twitter</td>
                            </tr>
                            </tbody>
                        </table>
                    </div>
                </div>


                <div class="my-3 p-3 bg-white rounded box-shadow">
                    <h6 class="border-bottom border-gray pb-2 mb-0">Recent updates</h6>

                    <table class="table table-sm">
                        <thead>
                        <tr>
                            <th scope="col">#</th>
                            <th scope="col">First</th>
                            <th scope="col">Last</th>
                            <th scope="col">Handle</th>
                        </tr>
                        </thead>
                        <tbody>
                        <tr>
                            <th scope="row">1</th>
                            <td>Mark</td>
                            <td>Otto</td>
                            <td>@mdo</td>
                        </tr>
                        <tr>
                            <th scope="row">2</th>
                            <td>Jacob</td>
                            <td>Thornton</td>
                            <td>@fat</td>
                        </tr>
                        <tr>
                            <th scope="row">3</th>
                            <td colspan="2">Larry the Bird</td>
                            <td>@twitter</td>
                        </tr>
                        </tbody>
                    </table>
                </div>
            </div><!--/span-->
        </div>
    </div>
</div>

<nav class="navbar fixed-bottom navbar-dark bg-dark navbar-expand-lg" style="border-top: 3px solid #F88529;">
    <div class="collapse navbar-collapse">
        <span class="navbar-text">
      Version: 1.0.0
        </span>
    </div>
</nav>

</body>
</html>