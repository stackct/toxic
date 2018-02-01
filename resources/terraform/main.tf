variable "aws_region"              { }
variable "aws_profile"             { }
variable "aws_access_key_id"       { }
variable "aws_secret_access_key"   { }
variable "environment"             { }
variable "vpc_id"                  { }
variable "subnet_id"               { }
variable "image_id"                { }
variable "instance_type"           { }
variable "private_key"             { }
variable "public_key"              { }
variable "ssh_user"                { }
variable "allowed_cidr"            { default = "0.0.0.0/0" }
variable "allowed_elb_cidr"        { default = "0.0.0.0/0" }
variable "root_device_size_gb"     { default = "50" }
variable "root_device_iops"        { default = "2500" }
variable "purpose"                 { default = "integration-testing" }
variable "dns_name"                { }
variable "external"                { default = 1 }
variable "remote_state_bucket"     { }
variable "remote_state_key_prefix" { }
variable "certificate_domain"      { }

/* This should match the profile that has access to read the remote state */
provider "aws" {
  region  = "${var.aws_region}"
  profile = "${var.aws_profile}"
}

resource "aws_security_group" "instance-sg" {
  name = "${var.environment}"
  description = "Basic security group for Toxic remote agent"
  vpc_id = "${var.vpc_id}"
  
  tags {
    Purpose = "${var.purpose}"
    Environment = "${var.environment}"
  }

  ingress {
      from_port = 22
      to_port = 22
      protocol = "tcp"
      cidr_blocks = "${split(",", var.allowed_cidr)}"
  }

  ingress {
      from_port = 80
      to_port = 80
      protocol = "tcp"

      security_groups = ["${aws_security_group.elb-sg.id}"]
  }  

  egress {
      from_port = 0
      to_port = 0
      protocol = "-1"
      cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_key_pair" "ec2-key" {
  key_name = "${var.environment}" 
  public_key = "${file("${var.public_key}")}"
}

resource "aws_instance" "instance" {
  ami                         = "${var.image_id}"
  instance_type               = "${var.instance_type}"
  key_name                    = "${aws_key_pair.ec2-key.key_name}"
  subnet_id                   = "${var.subnet_id}"
  vpc_security_group_ids      = ["${aws_security_group.instance-sg.id}"]
  user_data                   = "${data.template_file.user-data.rendered}"
  # TODO: Add instance profile with permissions to pull images from ECR

  lifecycle {
    ignore_changes = ["user_data"]
  }

  tags {
    Purpose     = "${var.purpose}"
    Name        = "${var.environment}"
    Environment = "${var.environment}"
  }

  provisioner "remote-exec" {
    connection {
        type = "ssh"
        user = "${var.ssh_user}"
        private_key = "${file("${var.private_key}")}"
    }
    inline = [ "while [ ! -f /tmp/signal ]; do sleep 2; done" ]
  }
}

data "terraform_remote_state" "hosted-zone" {
  backend = "s3"

  config {
    bucket = "${var.remote_state_bucket}"
    key = "${var.remote_state_key_prefix}/terraform.tfstate"
    profile = "${var.aws_profile}"
    region = "${var.aws_region}"
  }
}

data "template_file" "user-data" {
  template = "${file("${path.module}/user-data.tpl")}"

  vars {
    access_key_id = "${var.aws_access_key_id}"
    secret_access_key ="${var.aws_secret_access_key}"
    region = "${var.aws_region}"
  }  
}

resource "aws_route53_record" "zone-record" {
  count = "${var.external}"
  zone_id = "${data.terraform_remote_state.hosted-zone.public_zone_id}"
  name = "${var.dns_name}"
  type = "A"

  alias {
    name                   = "${aws_elb.lb-external.dns_name}"
    zone_id                = "${aws_elb.lb-external.zone_id}"
    evaluate_target_health = true
  }
}

data "aws_subnet" "selected" {
  id = "${var.subnet_id}"
}

data "aws_acm_certificate" "cert" {
  domain   = "${var.certificate_domain}"
  statuses = ["ISSUED"]
}

resource "aws_security_group" "elb-sg" {
  name = "elb-sg-${var.environment}"
  description = "Load balancer security group for ${var.environment}"
  vpc_id = "${var.vpc_id}"

  tags {
    Purpose = "${var.purpose}"
    Environment = "${var.environment}"
  }

  ingress {
      from_port = 443
      to_port = 443
      protocol = "tcp"
      cidr_blocks = "${split(",", var.allowed_elb_cidr)}"
  }  

  egress {
      from_port = 0
      to_port = 0
      protocol = "-1"
      cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_elb" "lb-external" {
  count = "${var.external}"
  name               = "lb-${var.dns_name}-${substr(uuid(), 0, 4)}"
  availability_zones = ["${data.aws_subnet.selected.availability_zone}"]

  listener {
    instance_port      = 80
    instance_protocol  = "http"
    lb_port            = 443
    lb_protocol        = "https"
    ssl_certificate_id = "${data.aws_acm_certificate.cert.arn}"
  }

  health_check {
    healthy_threshold   = 2
    unhealthy_threshold = 2
    timeout             = 5
    target              = "HTTP:80/health"
    interval            = 10
  }

  instances                   = ["${aws_instance.instance.id}"]
  cross_zone_load_balancing   = false
  idle_timeout                = 400
  connection_draining         = true
  connection_draining_timeout = 400

  security_groups = ["${aws_security_group.elb-sg.id}"]

  tags {
    Name = "lb-${var.environment}"
    Environment = "${var.environment}"
  }
}

output "instance_id" { 
  value = "${aws_instance.instance.id}"
}

output "public_ip" {
  value = "${aws_instance.instance.public_ip}"
}
