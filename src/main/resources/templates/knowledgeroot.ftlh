<!DOCTYPE html>
<html lang="en">
<head>

    <meta charset="utf-8">
    <title>Knowledgeroot</title>
    <meta name="description" content="Knowledgeroot Knowledgebase">
    <meta name="author" content="Knowledgeroot Team">

    <!-- Le HTML5 shim, for IE6-8 support of HTML elements -->
    <!--[if lt IE 9]>
    <script src="http://html5shim.googlecode.com/svn/trunk/html5.js"></script>
    <![endif]-->

    <!-- Bootstrap -->
    <link rel="stylesheet" href="webjars/bootstrap/css/bootstrap.min.css">

    <!-- Font Awesome -->
    <link rel="stylesheet" href="webjars/font-awesome/css/fontawesome.min.css">
    <link rel="stylesheet" href="webjars/font-awesome/css/solid.min.css">

    <!-- vuejs -->
    <script src="https://unpkg.com/vue@next"></script>

    <!-- axios -->
    <script src="https://cdn.jsdelivr.net/npm/axios/dist/axios.js"></script>

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

    </style>

    <!-- jQuery -->
    <script src="webjars/jquery/jquery.min.js"></script>

    <!-- Bootstrap -->
    <script src="webjars/bootstrap/js/bootstrap.min.js"></script>

    <!-- jstree -->
    <!--
    <link rel="stylesheet" href="webjars/jstree/themes/default/style.min.css" />
    <script src="webjars/jstree/jstree.min.js"></script>
    -->

    <!-- Knowledgeroot Vuejs App -->
    <script type="module" src="resources/Knowledgeroot.js"></script>
</head>
<body id="knowledgeroot-app">

<knowledgeroot-navbar-top></knowledgeroot-navbar-top>

<div class="container-fluid">
    <div class="row">
        <nav id="sidebarMenu" class="col-md-3 col-lg-2 d-md-block bg-light sidebar collapse">
            <div class="sidebar-sticky pt-3">
                <knowledgeroot-sidebar></knowledgeroot-sidebar>
            </div>
        </nav>

        <main role="main" class="col-md-9 ml-sm-auto col-lg-10 px-md-4">
            <div class="">
                <knowledgeroot-breadcrumb></knowledgeroot-breadcrumb>

                <div class="jumbotron">
                    <h1 class="display-4">Hello, world!</h1>
                    <p class="lead">This is a simple hero unit, a simple jumbotron-style component for calling extra attention to featured content or information.</p>
                    <hr class="my-4">
                    <p>It uses utility classes for typography and spacing to space content out within the larger container.</p>
                    <a class="btn btn-primary btn-lg" href="#" role="button">Learn more</a>
                </div>
            </div><!--/span-->
        </main>
    </div>
</div>

<!--
<knowledgeroot-navbar-bottom></knowledgeroot-navbar-bottom>
-->

</body>
</html>